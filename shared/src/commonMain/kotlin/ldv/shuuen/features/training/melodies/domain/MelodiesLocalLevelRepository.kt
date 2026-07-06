package ldv.shuuen.features.training.melodies.domain

import kotlinx.coroutines.flow.Flow
import ldv.shuuen.core.result.ResponseState

interface MelodiesLocalLevelRepository {

  fun getLevels(): Flow<ResponseState<List<MelodiesLevel>>>

  fun getLevelById(id: String): Flow<ResponseState<MelodiesLevel>>

  suspend fun upsertLevel(level: MelodiesLevel)

  suspend fun deleteLevel(id: String)
}
