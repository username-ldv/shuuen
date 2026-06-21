package ldv.shuuen.features.context.domain

import ldv.shuuen.core.music.DegreeContext

interface ContextLocalRepository {
  suspend fun getDegreeContextById(id: String?): DegreeContext?

  suspend fun upsertContext(context: DegreeContext)
}
