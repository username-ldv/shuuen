package ldv.shuuen.features.training.session.sync

import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.network.ApiJsonQualifier
import ldv.shuuen.core.network.apiErrorMessage
import ldv.shuuen.core.sync.SyncException
import ldv.shuuen.core.sync.SyncResult
import ldv.shuuen.core.sync.Synchronizer
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.level.apiName
import ldv.shuuen.features.training.session.QuestionResult
import ldv.shuuen.features.training.session.storage.TrainingSessionDao
import ldv.shuuen.features.training.session.storage.TrainingSessionDbEntity
import org.koin.core.annotation.Named

/** Syncs the finished training sessions with the user's backend account. */
internal class TrainingSessionSynchronizer(
  @Named("files") path: Path,
  private val api: TrainingSessionSyncApi,
  private val authRepository: AuthRepository,
  private val trainingSessionDao: TrainingSessionDao,
  @Named(ApiJsonQualifier) private val json: Json,
) : Synchronizer {
  private val store =
    storeOf<StoredTrainingSessionSyncRegistry>(file = Path(path, "training-session-sync.json"))
  private val mutex = Mutex()

  override suspend fun sync(): SyncResult = mutex.withLock {
    val authSession =
      authRepository.session.value
        ?: throw SyncException("Sign in before syncing training sessions.")
    val accountKey = "${authSession.backendUrl}\u0000${authSession.user.id}"
    var registry = store.get() ?: StoredTrainingSessionSyncRegistry()
    var account = registry.accounts[accountKey] ?: StoredTrainingSessionSyncAccount()
    var pushed = 0
    var received = 0
    var conflicts = 0
    var resetStaleCursor = false

    try {
      var hasMore = true
      do {
        val local = trainingSessionDao.getAll().associateBy { it.id }
        val pending = findPendingMutations(local, account).take(MaxChangesPerRequest)
        val response =
          try {
            api.sync(TrainingSessionSyncRequestDto(account.revision, pending)).data
          } catch (error: ResponseException) {
            if (error.response.status == HttpStatusCode.Conflict && !resetStaleCursor) {
              resetStaleCursor = true
              account = StoredTrainingSessionSyncAccount()
              registry = registry.copy(accounts = registry.accounts + (accountKey to account))
              store.set(registry)
              continue
            }
            throw error
          }
        require(response.revision >= account.revision) {
          "The backend returned an older training-session sync revision."
        }

        applyRemoteChanges(response.changes)
        val entries = account.sessions.toMutableMap()
        response.changes.forEach { change ->
          entries[change.id] =
            StoredTrainingSessionSyncEntry(
              revision = change.revision,
              fingerprint = change.takeUnless { it.deleted }?.fingerprint(),
            )
        }
        account = StoredTrainingSessionSyncAccount(response.revision, entries)
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
          HttpStatusCode.Conflict ->
            serverMessage ?: "The backend rejected the training-session changes."
          else -> serverMessage ?: "The backend couldn't sync the training sessions."
        }
      throw SyncException(message, error)
    } catch (error: Throwable) {
      throw SyncException(
        "Couldn't sync training sessions with ${authSession.backendUrl}.",
        error,
      )
    }

    SyncResult(pushed = pushed, received = received, conflicts = conflicts)
  }

  private fun findPendingMutations(
    local: Map<String, TrainingSessionDbEntity>,
    account: StoredTrainingSessionSyncAccount,
  ): List<TrainingSessionSyncMutationDto> = buildList {
    local.toSortedMap().forEach { (id, session) ->
      val baseline = account.sessions[id]
      if (baseline?.fingerprint != session.fingerprint()) {
        add(session.toMutation(baseRevision = baseline?.revision ?: 0))
      }
    }
    account.sessions.toSortedMap().forEach { (id, baseline) ->
      if (baseline.fingerprint != null && id !in local) {
        add(
          TrainingSessionSyncMutationDto(
            id = id,
            baseRevision = baseline.revision,
            deleted = true,
          )
        )
      }
    }
  }

  private suspend fun applyRemoteChanges(changes: List<TrainingSessionSyncChangeDto>) {
    val desired = linkedMapOf<String, TrainingSessionDbEntity?>()
    changes
      .sortedBy { it.revision }
      .forEach { change ->
        if (change.deleted) {
          desired[change.id] = null
          return@forEach
        }
        val flow =
          TrainingFlow.entries.firstOrNull { it.apiName == change.flow }
            ?: throw SyncException(
              "The backend returned an unknown training flow '${change.flow}'."
            )
        desired[change.id] =
          TrainingSessionDbEntity(
            id = change.id,
            flow = flow,
            levelId = change.levelId,
            levelName = change.levelName,
            completedAtEpochMillis = change.completedAtEpochMillis,
            finishedEarly = change.finishedEarly,
            questionsAnswered = change.questionsAnswered,
            notesTotal = change.notesTotal,
            correctNotes = change.correctNotes,
            missedNotes = change.missedNotes,
            replays = change.replays,
            durationMillis = change.durationMillis,
            avgAnswerMillis = change.avgAnswerMillis,
            avgDeltaMillis = change.avgDeltaMillis,
            bestStreak = change.bestStreak,
            keysPracticed = change.keysPracticed,
            questionResults = change.questionResults.map { it.toDomain() },
          )
      }
    trainingSessionDao.applySyncChanges(
      deletedIds = desired.filterValues { it == null }.keys.toList(),
      sessions = desired.values.filterNotNull(),
    )
  }

  private fun TrainingSessionDbEntity.toMutation(baseRevision: Long) =
    TrainingSessionSyncMutationDto(
      id = id,
      baseRevision = baseRevision,
      flow = flow.apiName,
      levelId = levelId,
      levelName = levelName,
      completedAtEpochMillis = completedAtEpochMillis,
      finishedEarly = finishedEarly,
      questionsAnswered = questionsAnswered,
      notesTotal = notesTotal,
      correctNotes = correctNotes,
      missedNotes = missedNotes,
      replays = replays,
      durationMillis = durationMillis,
      avgAnswerMillis = avgAnswerMillis,
      avgDeltaMillis = avgDeltaMillis,
      bestStreak = bestStreak,
      keysPracticed = keysPracticed,
      questionResults = questionResults.map { it.toDto() },
    )

  private fun TrainingSessionSyncChangeDto.toMutation() =
    TrainingSessionSyncMutationDto(
      id = id,
      baseRevision = 0,
      flow = flow,
      levelId = levelId,
      levelName = levelName,
      completedAtEpochMillis = completedAtEpochMillis,
      finishedEarly = finishedEarly,
      questionsAnswered = questionsAnswered,
      notesTotal = notesTotal,
      correctNotes = correctNotes,
      missedNotes = missedNotes,
      replays = replays,
      durationMillis = durationMillis,
      avgAnswerMillis = avgAnswerMillis,
      avgDeltaMillis = avgDeltaMillis,
      bestStreak = bestStreak,
      keysPracticed = keysPracticed,
      questionResults = questionResults,
    )

  private fun TrainingSessionDbEntity.fingerprint(): String =
    fingerprint(toMutation(baseRevision = 0))

  private fun TrainingSessionSyncChangeDto.fingerprint(): String = fingerprint(toMutation())

  private fun fingerprint(session: TrainingSessionSyncMutationDto): String {
    val canonical = json.encodeToString(session)
    var hash = 0xcbf29ce484222325uL
    canonical.encodeToByteArray().forEach { byte ->
      hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    }
    return hash.toString(16)
  }

  private fun QuestionResult.toDto() =
    TrainingQuestionResultDto(questionNumber, noteCount, missedCount)

  private fun TrainingQuestionResultDto.toDomain() =
    QuestionResult(questionNumber, noteCount, missedCount)

  private companion object {
    const val MaxChangesPerRequest = 500
  }
}
