package ldv.shuuen.core.sync

data class LevelSyncResult(
  val pushed: Int,
  val received: Int,
  val conflicts: Int,
)

class LevelSyncException(
  override val message: String,
  override val cause: Throwable? = null,
) : Exception(message, cause)

/** Manual, authenticated synchronization of the three local level collections. */
interface LevelSyncRepository {
  suspend fun sync(): LevelSyncResult
}
