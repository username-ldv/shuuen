package ldv.shuuen.data.repository.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.data.database.dao.ChordsLevelDao
import ldv.shuuen.data.database.entity.ChordsLevelDbEntity
import ldv.shuuen.features.context.domain.ContextLocalRepository
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository

class ChordsLocalLevelRepositoryImpl(
  private val chordsLevelDao: ChordsLevelDao,
  private val contextLocalRepository: ContextLocalRepository
) : ChordsLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<ChordsLevel>>> {
    return flow {
      emit(ResponseState.Loading)
      val entities = chordsLevelDao.getAll()
      emit(ResponseState.Success(entities.map { mapEntity(it) }))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override fun getLevelById(id: String): Flow<ResponseState<ChordsLevel>> {
    return flow {
      emit(ResponseState.Loading)
      val response =
        chordsLevelDao.getById(id)?.let { mapEntity(it) } ?: error("level with id $id not found")
      emit(ResponseState.Success(response))
    }.catch {
      emit(ResponseState.Error(it))
    }
  }

  override suspend fun upsertLevel(level: ChordsLevel) {
    level.context?.let { context ->
      if (context.source == ContextSource.UserLocal) {
        contextLocalRepository.upsertContext(context)
      }
    }
    val entity = ChordsLevelDbEntity(
      id = level.id,
      name = level.name,
      config = level.levelConfig,
      contextId = level.context?.id,
      source = level.source,
      questionsNumber = level.questionsNumber,
      range = level.range,
      chordSize = level.chordSize,
      sustainNotes = level.sustainNotes,
      answerOrder = level.answerOrder,
    )
    chordsLevelDao.upsertLevel(entity)
  }

  private suspend fun mapEntity(entity: ChordsLevelDbEntity): ChordsLevel {
    val context = contextLocalRepository.getDegreeContextById(entity.contextId)
    return ChordsLevel(
      id = entity.id,
      name = entity.name,
      levelConfig = entity.config,
      context = context,
      source = entity.source,
      questionsNumber = entity.questionsNumber,
      range = entity.range,
      chordSize = entity.chordSize,
      sustainNotes = entity.sustainNotes,
      answerOrder = entity.answerOrder,
    )
  }
}
