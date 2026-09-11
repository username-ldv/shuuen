package ldv.shuuen.data.repository.local

import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.entity.toDbEntity
import ldv.shuuen.data.database.entity.toDomainEntity
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.features.context.domain.ContextLocalRepository

class ContextLocalRepositoryImpl(private val contextDao: ContextDao) : ContextLocalRepository {
  override suspend fun getDegreeContextById(id: String?): DegreeContext? {
    return id?.let { contextDao.getById(id)?.toDomainEntity() }
  }

  override suspend fun upsertContext(context: DegreeContext) {
    contextDao.upsertContext(context.toDbEntity())
  }
}
