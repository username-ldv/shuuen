package ldv.shuuen.data.repository.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.data.database.dao.TrainingSessionDao
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository

class TrainingSessionRepositoryImpl(
  private val trainingSessionDao: TrainingSessionDao,
) : TrainingSessionRepository {
  override suspend fun saveSession(session: TrainingSession) {
    trainingSessionDao.upsertSession(session.toEntity())
  }

  override fun getSessionById(id: String): Flow<ResponseState<TrainingSession>> {
    return flow {
      emit(ResponseState.Loading)
      val session =
        trainingSessionDao.getById(id)?.toDomain() ?: error("session with id $id not found")
      emit(ResponseState.Success(session))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  private fun TrainingSession.toEntity() =
    TrainingSessionDbEntity(
      id = id,
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

  private fun TrainingSessionDbEntity.toDomain() =
    TrainingSession(
      id = id,
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
}
