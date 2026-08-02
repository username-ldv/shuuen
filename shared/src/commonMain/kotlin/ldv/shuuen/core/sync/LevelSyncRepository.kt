package ldv.shuuen.core.sync

data class LevelSyncResult(
  val pushed: Int,
  val received: Int,
  val conflicts: Int,
)

sealed interface LevelSyncStatus {
  data object Idle : LevelSyncStatus

  data object Syncing : LevelSyncStatus

  data class Complete(val result: LevelSyncResult) : LevelSyncStatus

  data class Failed(val message: String) : LevelSyncStatus
}

class LevelSyncException(
  override val message: String,
  override val cause: Throwable? = null,
) : Exception(message, cause)

/** Manual, authenticated synchronization of the three local level collections. */
interface LevelSyncRepository {
  suspend fun sync(): LevelSyncResult
}
