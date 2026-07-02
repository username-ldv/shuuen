package ldv.shuuen.features.training.level_end.domain

import kotlinx.coroutines.flow.Flow
import ldv.shuuen.core.result.ResponseState

interface TrainingSessionRepository {
  suspend fun saveSession(session: TrainingSession)

  fun getSessionById(id: String): Flow<ResponseState<TrainingSession>>
}
