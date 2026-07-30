package ldv.shuuen.core.online

import kotlinx.coroutines.flow.StateFlow

enum class BackendStatus {
  Checking,
  Available,
  Unavailable,
}

interface BackendStatusMonitor {
  val status: StateFlow<BackendStatus>

  /** Requests an immediate check, for example after saving the same URL again. */
  fun refresh()
}
