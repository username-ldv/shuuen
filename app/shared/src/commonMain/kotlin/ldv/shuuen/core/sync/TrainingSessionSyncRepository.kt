package ldv.shuuen.core.sync

data class TrainingSessionSyncResult(
  val pushed: Int,
  val received: Int,
  val conflicts: Int,
)

class TrainingSessionSyncException(
  override val message: String,
  override val cause: Throwable? = null,
) : Exception(message, cause)

/** Manual, authenticated synchronization of training history and its source statistics. */
interface TrainingSessionSyncRepository {
  suspend fun sync(): TrainingSessionSyncResult
}

data class DataSyncResult(
  val levels: LevelSyncResult,
  val trainingSessions: TrainingSessionSyncResult,
) {
  val pushed: Int
    get() = levels.pushed + trainingSessions.pushed

  val received: Int
    get() = levels.received + trainingSessions.received

  val conflicts: Int
    get() = levels.conflicts + trainingSessions.conflicts
}

sealed interface DataSyncStatus {
  data object Idle : DataSyncStatus

  data object Syncing : DataSyncStatus

  data class Complete(val result: DataSyncResult) : DataSyncStatus

  data class Failed(val message: String) : DataSyncStatus
}
