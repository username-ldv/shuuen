package ldv.shuuen.features.training.level.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.features.context.ContextRepository
import ldv.shuuen.features.training.level.SinglesLevel
import ldv.shuuen.features.training.level.SinglesLocalLevelRepository

class SinglesLocalLevelRepositoryImpl(
  private val singlesLevelDao: SinglesLevelDao,
  private val contextRepository: ContextRepository,
) : SinglesLocalLevelRepository {
  override fun observeLevels(): Flow<List<SinglesLevel>> =
    singlesLevelDao.observeAll().map { levels -> levels.map { it.toLevel() } }

  override suspend fun getLevel(id: String): SinglesLevel? = singlesLevelDao.getById(id)?.toLevel()

  override suspend fun upsertLevel(level: SinglesLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
        contextRepository.upsertContext(context)
      }
    }
    singlesLevelDao.upsertLevel(level.toDbEntity())
  }

  override suspend fun deleteLevel(id: String) {
    singlesLevelDao.deleteById(id)
  }
}
