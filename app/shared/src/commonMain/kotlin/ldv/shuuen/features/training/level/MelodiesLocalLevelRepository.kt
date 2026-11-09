package ldv.shuuen.features.training.level

import kotlinx.coroutines.flow.Flow

/** The user's own Melodies levels, stored on the device. */
interface MelodiesLocalLevelRepository {
  /** Every stored level; emits again after any change to them or their contexts. */
  fun observeLevels(): Flow<List<MelodiesLevel>>

  /** The level with [id], or null when there is none. */
  suspend fun getLevel(id: String): MelodiesLevel?

  suspend fun upsertLevel(level: MelodiesLevel)

  suspend fun deleteLevel(id: String)
}
