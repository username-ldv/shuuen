package ldv.shuuen.data.remote.sync

import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.sync.LevelSyncException
import ldv.shuuen.core.sync.LevelSyncRepository
import ldv.shuuen.core.sync.LevelSyncResult
import ldv.shuuen.data.database.dao.ChordsLevelDao
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.database.entity.ChordsLevelDbEntity
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity
import ldv.shuuen.data.database.entity.toDbEntity
import ldv.shuuen.data.database.entity.toDomainEntity
import ldv.shuuen.data.remote.auth.ApiErrorDto
import ldv.shuuen.data.remote.ApiJsonQualifier
import ldv.shuuen.data.remote.course.LevelDefinitionCodec
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.apiName
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel
import org.koin.core.annotation.Named

internal class LevelSyncRepositoryImpl(
  @Named("files") path: Path,
  private val api: LevelSyncApi,
  private val authRepository: AuthRepository,
  private val singlesDao: SinglesLevelDao,
  private val melodiesDao: MelodiesLevelDao,
  private val chordsDao: ChordsLevelDao,
  private val contextDao: ContextDao,
  private val codec: LevelDefinitionCodec,
  @Named(ApiJsonQualifier) private val json: Json,
) : LevelSyncRepository {
  private val store = storeOf<StoredLevelSyncRegistry>(file = Path(path, "level-sync.json"))
  private val mutex = Mutex()

  override suspend fun sync(): LevelSyncResult = mutex.withLock {
    val session =
      authRepository.session.value
        ?: throw LevelSyncException("Sign in before syncing levels.")
    val accountKey = "${session.backendUrl}\u0000${session.user.id}"
    var registry = store.get() ?: StoredLevelSyncRegistry()
    var account = registry.accounts[accountKey] ?: StoredLevelSyncAccount()
    var pushed = 0
    var received = 0
    var conflicts = 0
    var resetStaleCursor = false

    try {
      var hasMore = true
      do {
        val local = readLocalLevels()
        val pending = findPendingMutations(local, account).take(MaxChangesPerRequest)
        val response =
          try {
            api.sync(
              baseUrl = session.backendUrl,
              accessToken = session.accessToken,
              request = LevelSyncRequestDto(account.revision, pending),
            ).data
          } catch (error: ResponseException) {
            // A restored/recreated backend can legitimately have a lower cursor.
            // Reset this account's baseline once and let the ordinary first-sync
            // merge rules rebuild it from the local and server copies.
            if (error.response.status == HttpStatusCode.Conflict && !resetStaleCursor) {
              resetStaleCursor = true
              account = StoredLevelSyncAccount()
              registry = registry.copy(accounts = registry.accounts + (accountKey to account))
              store.set(registry)
              continue
            }
            throw error
          }
        require(response.revision >= account.revision) {
          "The backend returned an older sync revision."
        }

        applyRemoteChanges(response.changes)
        val entries = account.levels.toMutableMap()
        response.changes.forEach { change ->
          val key = levelKey(change.kind, change.id)
          entries[key] =
            StoredLevelSyncEntry(
              revision = change.revision,
              fingerprint = change.takeUnless { it.deleted }?.fingerprint(),
            )
        }
        account = StoredLevelSyncAccount(response.revision, entries)
        registry = registry.copy(accounts = registry.accounts + (accountKey to account))
        store.set(registry)

        pushed += response.applied
        received += response.changes.size
        conflicts += response.conflicts
        hasMore = pending.size == MaxChangesPerRequest
      } while (hasMore)
    } catch (error: CancellationException) {
      throw error
    } catch (error: LevelSyncException) {
      throw error
    } catch (error: ResponseException) {
      val serverMessage =
        runCatching { error.response.body<ApiErrorDto>().error }
          .getOrNull()
          ?.takeIf { it.isNotBlank() }
      val message =
        when (error.response.status) {
          HttpStatusCode.Unauthorized -> "Your session has expired. Sign in again."
          HttpStatusCode.BadRequest, HttpStatusCode.Conflict ->
            serverMessage ?: "The backend rejected the level changes."
          else -> serverMessage ?: "The backend couldn't sync the levels."
        }
      throw LevelSyncException(message, error)
    } catch (error: Throwable) {
      throw LevelSyncException("Couldn't sync levels with ${session.backendUrl}.", error)
    }

    LevelSyncResult(pushed = pushed, received = received, conflicts = conflicts)
  }

  private suspend fun readLocalLevels(): Map<String, LocalLevel> {
    val contexts = contextDao.getAll().associate { it.id to it.toDomainEntity() }
    val levels = buildList {
      singlesDao.getAll().forEach { entity ->
        val level =
          SinglesLevel(
            id = entity.id,
            name = entity.name,
            levelConfig = entity.config,
            context = contexts[entity.contextId],
            source = entity.source,
            questionsNumber = entity.questionsNumber,
            range = entity.range,
          )
        add(LocalLevel(TrainingFlow.Singles, level.id, level.name, level.source, codec.encode(level)))
      }
      melodiesDao.getAll().forEach { entity ->
        val level =
          MelodiesLevel(
            id = entity.id,
            name = entity.name,
            config = entity.config,
            context = contexts[entity.contextId],
            source = entity.source,
          )
        add(LocalLevel(TrainingFlow.Melodies, level.id, level.name, level.source, codec.encode(level)))
      }
      chordsDao.getAll().forEach { entity ->
        val level =
          ChordsLevel(
            id = entity.id,
            name = entity.name,
            levelConfig = entity.config,
            context = contexts[entity.contextId],
            source = entity.source,
            questionsNumber = entity.questionsNumber,
            range = entity.range,
            chordSize = entity.chordSize,
            sustainNotes = entity.sustainNotes,
            answerOrder = entity.answerOrder,
          )
        add(LocalLevel(TrainingFlow.Chords, level.id, level.name, level.source, codec.encode(level)))
      }
    }
    return levels.associateBy { it.key }
  }

  private fun findPendingMutations(
    local: Map<String, LocalLevel>,
    account: StoredLevelSyncAccount,
  ): List<LevelSyncMutationDto> = buildList {
    local.toSortedMap().forEach { (key, level) ->
      val baseline = account.levels[key]
      if (baseline?.fingerprint != level.fingerprint()) {
        add(
          LevelSyncMutationDto(
            kind = level.flow.apiName,
            id = level.id,
            baseRevision = baseline?.revision ?: 0,
            name = level.name,
            source = level.source.dbValue,
            definition = level.definition,
          )
        )
      }
    }
    account.levels.toSortedMap().forEach { (key, baseline) ->
      if (baseline.fingerprint != null && key !in local) {
        val separator = key.indexOf('\u0000')
        add(
          LevelSyncMutationDto(
            kind = key.substring(0, separator),
            id = key.substring(separator + 1),
            baseRevision = baseline.revision,
            deleted = true,
          )
        )
      }
    }
  }

  private suspend fun applyRemoteChanges(changes: List<LevelSyncChangeDto>) {
    changes.sortedBy { it.revision }.forEach { change ->
      val flow = TrainingFlow.entries.firstOrNull { it.apiName == change.kind }
        ?: throw LevelSyncException("The backend returned an unknown level kind '${change.kind}'.")
      if (change.deleted) {
        when (flow) {
          TrainingFlow.Singles -> singlesDao.deleteById(change.id)
          TrainingFlow.Melodies -> melodiesDao.deleteById(change.id)
          TrainingFlow.Chords -> chordsDao.deleteById(change.id)
        }
        return@forEach
      }
      val source = LevelSource.entries.firstOrNull { it.dbValue == change.source }
        ?: throw LevelSyncException("The backend returned an unknown level source '${change.source}'.")
      val definition = change.definition
        ?: throw LevelSyncException("The backend returned a level without its definition.")
      when (val decoded = codec.decode(flow, change.id, change.name, source, definition)) {
        is PlayableTrainingLevel.Singles -> upsert(decoded.level)
        is PlayableTrainingLevel.Melodies -> upsert(decoded.level)
        is PlayableTrainingLevel.Chords -> upsert(decoded.level)
      }
    }
  }

  private suspend fun upsert(level: SinglesLevel) {
    upsertContext(level.context)
    singlesDao.upsertLevel(
      SinglesLevelDbEntity(
        level.id,
        level.name,
        level.levelConfig,
        level.context?.id,
        level.source,
        level.questionsNumber,
        level.range,
      )
    )
  }

  private suspend fun upsert(level: MelodiesLevel) {
    upsertContext(level.context)
    melodiesDao.upsertLevel(
      MelodiesLevelDbEntity(level.id, level.name, level.config, level.context?.id, level.source)
    )
  }

  private suspend fun upsert(level: ChordsLevel) {
    upsertContext(level.context)
    chordsDao.upsertLevel(
      ChordsLevelDbEntity(
        level.id,
        level.name,
        level.levelConfig,
        level.context?.id,
        level.source,
        level.questionsNumber,
        level.range,
        level.chordSize,
        level.sustainNotes,
        level.answerOrder,
      )
    )
  }

  private suspend fun upsertContext(context: DegreeContext?) {
    context?.let { contextDao.upsertContext(it.toDbEntity()) }
  }

  private data class LocalLevel(
    val flow: TrainingFlow,
    val id: String,
    val name: String,
    val source: LevelSource,
    val definition: JsonElement,
  ) {
    val key: String = levelKey(flow.apiName, id)
  }

  private fun LocalLevel.fingerprint(): String =
    fingerprint(flow.apiName, id, name, source.dbValue, definition)

  private fun LevelSyncChangeDto.fingerprint(): String {
    val definition = definition
      ?: throw LevelSyncException("The backend returned a level without its definition.")
    return fingerprint(kind, id, name, source, definition)
  }

  private fun fingerprint(
    kind: String,
    id: String,
    name: String,
    source: String,
    definition: JsonElement,
  ): String {
    val canonical =
      json.encodeToString(listOf(kind, id, name, source, canonicalJson(definition).toString()))
    var hash = 0xcbf29ce484222325uL
    canonical.encodeToByteArray().forEach { byte ->
      hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    }
    return hash.toString(16)
  }

  /** Matches encoding/json's recursively sorted object-key representation on the backend. */
  private fun canonicalJson(element: JsonElement): JsonElement =
    when (element) {
      is JsonObject ->
        JsonObject(element.keys.sorted().associateWith { key -> canonicalJson(element.getValue(key)) })
      is JsonArray -> JsonArray(element.map(::canonicalJson))
      else -> element
    }

  private companion object {
    const val MaxChangesPerRequest = 500

    fun levelKey(kind: String, id: String): String = "$kind\u0000$id"
  }
}
