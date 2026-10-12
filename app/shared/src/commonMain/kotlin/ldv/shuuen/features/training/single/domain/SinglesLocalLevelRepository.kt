package ldv.shuuen.features.training.single.domain

import kotlinx.coroutines.flow.Flow
import ldv.shuuen.core.result.ResponseState

interface SinglesLocalLevelRepository {

  fun getLevels(): Flow<ResponseState<List<SinglesLevel>>>

  fun getLevelById(id: String): Flow<ResponseState<SinglesLevel>>

  suspend fun upsertLevel(level: SinglesLevel)

  suspend fun deleteLevel(id: String)
}
