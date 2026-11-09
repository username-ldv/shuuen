package ldv.shuuen.core.sync

/** What a sync run did: changes sent, changes received, and conflicts the backend resolved. */
data class SyncResult(
  val pushed: Int,
  val received: Int,
  val conflicts: Int,
) {
  operator fun plus(other: SyncResult) =
    SyncResult(
      pushed = pushed + other.pushed,
      received = received + other.received,
      conflicts = conflicts + other.conflicts,
    )

  companion object {
    val None = SyncResult(pushed = 0, received = 0, conflicts = 0)
  }
}

/** A sync failure whose [message] is meant for the user. */
class SyncException(
  override val message: String,
  override val cause: Throwable? = null,
) : Exception(message, cause)

/**
 * One kind of data that syncs with the backend, e.g. the user's levels. Bind it with
 * `bind Synchronizer::class` and [DataSync] runs it. [sync] throws when it fails, preferably a
 * [SyncException].
 */
interface Synchronizer {
  suspend fun sync(): SyncResult
}
