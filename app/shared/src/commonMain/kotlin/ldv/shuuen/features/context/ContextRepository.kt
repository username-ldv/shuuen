package ldv.shuuen.features.context

import ldv.shuuen.core.music.DegreeContext

/** The locally stored contexts: those a level uses, saved along with it. */
class ContextRepository(private val contextDao: ContextDao) {
  suspend fun getContext(id: String): DegreeContext? = contextDao.getById(id)?.toDomainEntity()

  suspend fun getContexts(): List<DegreeContext> = contextDao.getAll().map { it.toDomainEntity() }

  suspend fun upsertContext(context: DegreeContext) {
    contextDao.upsertContext(context.toDbEntity())
  }
}
