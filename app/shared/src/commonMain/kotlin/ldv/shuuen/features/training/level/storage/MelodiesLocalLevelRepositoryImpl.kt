package ldv.shuuen.features.training.level.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.features.context.ContextRepository
import ldv.shuuen.features.training.level.MelodiesLevel
import ldv.shuuen.features.training.level.MelodiesLocalLevelRepository

class MelodiesLocalLevelRepositoryImpl(
  private val melodiesLevelDao: MelodiesLevelDao,
  private val contextRepository: ContextRepository,
) : MelodiesLocalLevelRepository {
  override fun observeLevels(): Flow<List<MelodiesLevel>> =
    melodiesLevelDao.observeAll().map { levels -> levels.map { it.toLevel() } }

  override suspend fun getLevel(id: String): MelodiesLevel? =
    melodiesLevelDao.getById(id)?.toLevel()

  override suspend fun upsertLevel(level: MelodiesLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
        contextRepository.upsertContext(context)
      }
    }
    melodiesLevelDao.upsertLevel(level.toDbEntity())
  }

  override suspend fun deleteLevel(id: String) {
    melodiesLevelDao.deleteById(id)
  }
}
