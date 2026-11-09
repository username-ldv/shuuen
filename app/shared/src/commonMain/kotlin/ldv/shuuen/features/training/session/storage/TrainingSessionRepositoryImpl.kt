package ldv.shuuen.features.training.session.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.session.LevelAccuracySample
import ldv.shuuen.features.training.session.LevelAccuracyStats
import ldv.shuuen.features.training.session.TrainingSession
import ldv.shuuen.features.training.session.TrainingSessionRepository
import ldv.shuuen.features.training.session.levelAccuracyStats

class TrainingSessionRepositoryImpl(private val trainingSessionDao: TrainingSessionDao) :
  TrainingSessionRepository {
  override suspend fun saveSession(session: TrainingSession) {
    trainingSessionDao.upsertSession(session.toEntity())
  }

  override suspend fun getSession(id: String): TrainingSession? =
    trainingSessionDao.getById(id)?.toDomain()

  override fun observeLatestSession(): Flow<TrainingSession?> =
    trainingSessionDao.observeLatest().map { it?.toDomain() }.catch { emit(null) }

  override fun observeLevelAccuracyStats(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<LevelAccuracyStats> {
    val coercedLimit = coerceLevelStatsWindow(limit)
    return trainingSessionDao
      .observeRecentScoresByLevelId(flow, levelId, coercedLimit)
      .map { scores ->
        levelAccuracyStats(
          samples =
            scores.map { score ->
              LevelAccuracySample(
                correctNotes = score.correctNotes,
                notesTotal = score.notesTotal,
              )
            },
          windowSize = coercedLimit,
        )
      }
      .catch { emit(LevelAccuracyStats(windowSize = coercedLimit)) }
  }

  override fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<Set<String>> =
    trainingSessionDao.observeAttemptedLevelIds(flow).map { it.toSet() }.catch { emit(emptySet()) }

  override fun observeCompletedLevelIds(flow: TrainingFlow): Flow<Set<String>> =
    trainingSessionDao.observeCompletedLevelIds(flow).map { it.toSet() }.catch { emit(emptySet()) }

  override suspend fun deleteLastLevelSession(flow: TrainingFlow, levelId: String) {
    trainingSessionDao.deleteLastByLevelId(flow, levelId)
  }

  override suspend fun deleteAllLevelSessions(flow: TrainingFlow, levelId: String) {
    trainingSessionDao.deleteAllByLevelId(flow, levelId)
  }

  override suspend fun deleteAllCourseSessions(courseId: Long) {
    require(courseId > 0) { "A course ID must be positive." }
    trainingSessionDao.deleteAllByCourseReferencePrefix("course:$courseId:")
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
