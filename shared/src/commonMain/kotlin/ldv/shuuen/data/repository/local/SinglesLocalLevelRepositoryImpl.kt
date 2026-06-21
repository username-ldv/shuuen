package ldv.shuuen.data.repository.local

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity
import ldv.shuuen.features.context.domain.ContextLocalRepository
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.features.training.single.domain.SinglesLevel

class SinglesLocalLevelRepositoryImpl(
  private val singlesLevelDao: SinglesLevelDao,
  private val contextLocalRepository: ContextLocalRepository
) : SinglesLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<SinglesLevel>>> {
    return flow {
      emit(ResponseState.Loading)
      Napier.v { "getting levels" }
      val entities = singlesLevelDao.getAll()
      Napier.v { "got entities, size: ${entities.size}" }
      emit(ResponseState.Success(entities.map { mapEntity(it) }))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override fun getLevelById(id: String): Flow<ResponseState<SinglesLevel>> {
    return flow {
      emit(ResponseState.Loading)
      val response =
        singlesLevelDao.getById(id)?.let { mapEntity(it) } ?: error("level with id $id not found")
      emit(ResponseState.Success(response))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override suspend fun upsertLevel(level: SinglesLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
       contextLocalRepository.upsertContext(context)
      }
    }
    val entity = SinglesLevelDbEntity(
      id = level.id,
      name = level.name,
      config = level.levelConfig,
      contextId = level.context?.id,
      source = level.source,
      questionsNumber = level.questionsNumber,
      range = level.range,
    )
    singlesLevelDao.upsertLevel(entity)
  }

  private suspend fun mapEntity(entity: SinglesLevelDbEntity): SinglesLevel {
    val context = contextLocalRepository.getDegreeContextById(entity.contextId)
    return SinglesLevel(
      id = entity.id,
      name = entity.name,
      levelConfig = entity.config,
      context = context,
      source = entity.source,
      questionsNumber = entity.questionsNumber,
      range = entity.range
    )
  }
}