package ldv.shuuen.data.repository.local

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity
import ldv.shuuen.features.context.domain.ContextLocalRepository
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository

class MelodiesLocalLevelRepositoryImpl(
  private val melodiesLevelDao: MelodiesLevelDao,
  private val contextLocalRepository: ContextLocalRepository
) : MelodiesLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<MelodiesLevel>>> {
    return flow {
      emit(ResponseState.Loading)
      val entities = melodiesLevelDao.getAll()
      Napier.v { "got melodies level entities, size: ${entities.size}" }
      emit(ResponseState.Success(entities.map { mapEntity(it) }))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override fun getLevelById(id: String): Flow<ResponseState<MelodiesLevel>> {
    return flow {
      emit(ResponseState.Loading)
      val response =
        melodiesLevelDao.getById(id)?.let { mapEntity(it) } ?: error("level with id $id not found")
      emit(ResponseState.Success(response))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override suspend fun upsertLevel(level: MelodiesLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
        contextLocalRepository.upsertContext(context)
      }
    }
    val entity = MelodiesLevelDbEntity(
      id = level.id,
      name = level.name,
      config = level.config,
      contextId = level.context?.id,
      source = level.source,
    )
    melodiesLevelDao.upsertLevel(entity)
  }

  private suspend fun mapEntity(entity: MelodiesLevelDbEntity): MelodiesLevel {
    val context = contextLocalRepository.getDegreeContextById(entity.contextId)
    return MelodiesLevel(
      id = entity.id,
      name = entity.name,
      config = entity.config,
      context = context,
      source = entity.source,
    )
  }
}
