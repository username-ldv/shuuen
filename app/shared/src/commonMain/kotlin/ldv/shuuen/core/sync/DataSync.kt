package ldv.shuuen.core.sync

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/**
 * The one sync entry point: runs every [Synchronizer], even when one of them fails, and adds up
 * their results. Sync is manual, so nothing schedules it.
 */
class DataSync(private val synchronizers: List<Synchronizer>) {
  suspend fun syncAll(): DataSyncReport {
    var result = SyncResult.None
    val failures = mutableListOf<Throwable>()
    synchronizers.forEach { synchronizer ->
      try {
        result += synchronizer.sync()
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        Napier.w(error) { "${synchronizer::class.simpleName} failed" }
        failures += error
      }
    }
    return DataSyncReport(result, failures)
  }
}

/**
 * [result] sums the synchronizers that succeeded; [failures] holds the errors of those that
 * didn't, in run order.
 */
data class DataSyncReport(
  val result: SyncResult,
  val failures: List<Throwable>,
) {
  val succeeded: Boolean
    get() = failures.isEmpty()
}
