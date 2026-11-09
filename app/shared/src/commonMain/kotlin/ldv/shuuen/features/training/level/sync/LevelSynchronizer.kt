package ldv.shuuen.features.training.level.sync

import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.network.ApiJsonQualifier
import ldv.shuuen.core.network.apiErrorMessage
import ldv.shuuen.core.sync.SyncException
import ldv.shuuen.core.sync.SyncResult
import ldv.shuuen.core.sync.Synchronizer
import ldv.shuuen.features.context.ContextRepository
import ldv.shuuen.features.training.level.ChordsLevel
import ldv.shuuen.features.training.level.LevelSource
import ldv.shuuen.features.training.level.MelodiesLevel
import ldv.shuuen.features.training.level.PlayableTrainingLevel
import ldv.shuuen.features.training.level.SinglesLevel
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.level.apiName
import ldv.shuuen.features.training.level.definition.LevelDefinitionCodec
import ldv.shuuen.features.training.level.storage.ChordsLevelDao
import ldv.shuuen.features.training.level.storage.MelodiesLevelDao
import ldv.shuuen.features.training.level.storage.SinglesLevelDao
import ldv.shuuen.features.training.level.storage.toDbEntity
import ldv.shuuen.features.training.level.storage.toLevel
import org.koin.core.annotation.Named

/** Syncs the user's own levels of every flow with their backend account. */
internal class LevelSynchronizer(
  @Named("files") path: Path,
  private val api: LevelSyncApi,
  private val authRepository: AuthRepository,
  private val singlesDao: SinglesLevelDao,
  private val melodiesDao: MelodiesLevelDao,
  private val chordsDao: ChordsLevelDao,
  private val contextRepository: ContextRepository,
  private val codec: LevelDefinitionCodec,
  @Named(ApiJsonQualifier) private val json: Json,
) : Synchronizer {
  private val store = storeOf<StoredLevelSyncRegistry>(file = Path(path, "level-sync.json"))
  private val mutex = Mutex()

  override suspend fun sync(): SyncResult = mutex.withLock {
    val session =
      authRepository.session.value ?: throw SyncException("Sign in before syncing levels.")
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
            api.sync(LevelSyncRequestDto(account.revision, pending)).data
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
    } catch (error: SyncException) {
      throw error
    } catch (error: ResponseException) {
      val serverMessage = error.apiErrorMessage()
      val message =
        when (error.response.status) {
          HttpStatusCode.Unauthorized -> "Your session has expired. Sign in again."
          HttpStatusCode.BadRequest,
          HttpStatusCode.Conflict -> serverMessage ?: "The backend rejected the level changes."
          else -> serverMessage ?: "The backend couldn't sync the levels."
        }
      throw SyncException(message, error)
    } catch (error: Throwable) {
      throw SyncException("Couldn't sync levels with ${session.backendUrl}.", error)
    }

    SyncResult(pushed = pushed, received = received, conflicts = conflicts)
  }

  private suspend fun readLocalLevels(): Map<String, LocalLevel> {
    val contexts = contextRepository.getContexts().associateBy { it.id }
    fun context(id: String?) = id?.let(contexts::get)
    val levels = buildList {
      singlesDao.getAll().forEach { entity ->
        val level = entity.toLevel(context(entity.contextId))
        add(
          LocalLevel(TrainingFlow.Singles, level.id, level.name, level.source, codec.encode(level))
        )
      }
      melodiesDao.getAll().forEach { entity ->
        val level = entity.toLevel(context(entity.contextId))
        add(
          LocalLevel(TrainingFlow.Melodies, level.id, level.name, level.source, codec.encode(level))
        )
      }
      chordsDao.getAll().forEach { entity ->
        val level = entity.toLevel(context(entity.contextId))
        add(
          LocalLevel(TrainingFlow.Chords, level.id, level.name, level.source, codec.encode(level))
        )
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
    changes
      .sortedBy { it.revision }
      .forEach { change ->
        val flow =
          TrainingFlow.entries.firstOrNull { it.apiName == change.kind }
            ?: throw SyncException(
              "The backend returned an unknown level kind '${change.kind}'."
            )
        if (change.deleted) {
          when (flow) {
            TrainingFlow.Singles -> singlesDao.deleteById(change.id)
            TrainingFlow.Melodies -> melodiesDao.deleteById(change.id)
            TrainingFlow.Chords -> chordsDao.deleteById(change.id)
          }
          return@forEach
        }
        val source =
          LevelSource.entries.firstOrNull { it.dbValue == change.source }
            ?: throw SyncException(
              "The backend returned an unknown level source '${change.source}'."
            )
        val definition =
          change.definition
            ?: throw SyncException("The backend returned a level without its definition.")
        when (val decoded = codec.decode(flow, change.id, change.name, source, definition)) {
          is PlayableTrainingLevel.Singles -> upsert(decoded.level)
          is PlayableTrainingLevel.Melodies -> upsert(decoded.level)
          is PlayableTrainingLevel.Chords -> upsert(decoded.level)
        }
      }
  }

  private suspend fun upsert(level: SinglesLevel) {
    upsertContext(level.context)
    singlesDao.upsertLevel(level.toDbEntity())
  }

  private suspend fun upsert(level: MelodiesLevel) {
    upsertContext(level.context)
    melodiesDao.upsertLevel(level.toDbEntity())
  }

  private suspend fun upsert(level: ChordsLevel) {
    upsertContext(level.context)
    chordsDao.upsertLevel(level.toDbEntity())
  }

  private suspend fun upsertContext(context: DegreeContext?) {
    context?.let { contextRepository.upsertContext(it) }
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
    val definition =
      definition ?: throw SyncException("The backend returned a level without its definition.")
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
        JsonObject(
          element.keys.sorted().associateWith { key -> canonicalJson(element.getValue(key)) }
        )
      is JsonArray -> JsonArray(element.map(::canonicalJson))
      else -> element
    }

  private companion object {
    const val MaxChangesPerRequest = 500

    fun levelKey(kind: String, id: String): String = "$kind\u0000$id"
  }
}
