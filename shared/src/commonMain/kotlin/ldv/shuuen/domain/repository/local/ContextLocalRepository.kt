package ldv.shuuen.domain.repository.local

import ldv.shuuen.domain.audio.music.DegreeContext

interface ContextLocalRepository {
  suspend fun getDegreeContextById(id: String?): DegreeContext?

  suspend fun upsertContext(context: DegreeContext)
}