package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
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
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor

internal class KtorBackendStatusMonitor(
  private val client: HttpClient,
  private val config: ApiConfig,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BackendStatusMonitor {
  private val mutableStatus = MutableStateFlow(BackendStatus.Checking)
  override val status = mutableStatus.asStateFlow()
  private var checkJob: Job? = null

  override fun refresh() {
    checkJob?.cancel()
    checkJob = scope.launch {
      mutableStatus.value = BackendStatus.Checking
      mutableStatus.value = check(config.currentBaseUrl())
    }
  }

  private suspend fun check(baseUrl: String): BackendStatus =
    try {
      withTimeout(CheckTimeoutMillis) {
        client.get {
          url(baseUrl)
          url {
            appendPathSegments("api", "v1", "courses")
            parameters.append("limit", "1")
            parameters.append("offset", "0")
          }
        }.bodyAsText()
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
