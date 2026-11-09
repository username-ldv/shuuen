package ldv.shuuen.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal class KtorBackendStatusMonitor(
  private val client: HttpClient,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BackendStatusMonitor {
  private val mutableStatus = MutableStateFlow(BackendStatus.Checking)
  override val status = mutableStatus.asStateFlow()
  private var checkJob: Job? = null

  override fun refresh() {
    checkJob?.cancel()
    checkJob = scope.launch {
      mutableStatus.value = BackendStatus.Checking
      mutableStatus.value = check()
    }
  }

  private suspend fun check(): BackendStatus =
    try {
      withTimeout(CheckTimeoutMillis) {
        client
          .get("api/v1/courses") {
            parameter("limit", 1)
            parameter("offset", 0)
          }
          .bodyAsTextAndClose()
      }
      BackendStatus.Available
    } catch (_: TimeoutCancellationException) {
      BackendStatus.Unavailable
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      BackendStatus.Unavailable
    }

  private companion object {
    const val CheckTimeoutMillis = 3_000L
  }
}
