package ldv.shuuen.features.training.level_end.domain

import kotlinx.coroutines.flow.Flow
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.TrainingFlow

interface TrainingSessionRepository {
  suspend fun saveSession(session: TrainingSession)

  fun getSessionById(id: String): Flow<ResponseState<TrainingSession>>

  fun observeLevelAccuracyStats(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<LevelAccuracyStats>

  fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<Set<String>>

  suspend fun deleteLastLevelSession(flow: TrainingFlow, levelId: String)

  suspend fun deleteAllLevelSessions(flow: TrainingFlow, levelId: String)

  suspend fun deleteAllCourseSessions(courseId: Long)
}
