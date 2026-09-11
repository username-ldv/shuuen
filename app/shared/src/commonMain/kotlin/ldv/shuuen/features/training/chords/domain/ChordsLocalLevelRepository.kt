package ldv.shuuen.features.training.chords.domain

import kotlinx.coroutines.flow.Flow
import ldv.shuuen.core.result.ResponseState

interface ChordsLocalLevelRepository {

  fun getLevels(): Flow<ResponseState<List<ChordsLevel>>>

  fun getLevelById(id: String): Flow<ResponseState<ChordsLevel>>

  suspend fun upsertLevel(level: ChordsLevel)

  suspend fun deleteLevel(id: String)
}
