package ldv.shuuen.features.training.level

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.features.context.ContextRepository

class ChordsLocalLevelRepositoryImpl(
  private val chordsLevelDao: ChordsLevelDao,
  private val contextRepository: ContextRepository,
) : ChordsLocalLevelRepository {
  override fun observeLevels(): Flow<List<ChordsLevel>> =
    chordsLevelDao.observeAll().map { levels -> levels.map { it.toLevel() } }

  override suspend fun getLevel(id: String): ChordsLevel? = chordsLevelDao.getById(id)?.toLevel()

  override suspend fun upsertLevel(level: ChordsLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
        contextRepository.upsertContext(context)
      }
    }
    chordsLevelDao.upsertLevel(level.toDbEntity())
  }

  override suspend fun deleteLevel(id: String) {
    chordsLevelDao.deleteById(id)
  }
}
