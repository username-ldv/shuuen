package ldv.shuuen.core.online

import kotlinx.coroutines.flow.StateFlow

enum class BackendStatus {
  Checking,
  Available,
  Unavailable,
}

interface BackendStatusMonitor {
  val status: StateFlow<BackendStatus>

  /** Runs a single availability check against the currently configured backend URL. */
  fun refresh()
}
