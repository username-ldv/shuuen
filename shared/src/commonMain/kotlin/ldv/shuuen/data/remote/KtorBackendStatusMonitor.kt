package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor

internal class KtorBackendStatusMonitor(
  private val client: HttpClient,
  private val config: ApiConfig,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BackendStatusMonitor {
  private val mutableStatus = MutableStateFlow(BackendStatus.Checking)
  override val status = mutableStatus.asStateFlow()
  private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  init {
    scope.launch {
      config.baseUrl.collectLatest { baseUrl ->
        mutableStatus.value = BackendStatus.Checking
        while (currentCoroutineContext().isActive) {
          mutableStatus.value = check(baseUrl)
          withTimeoutOrNull(CheckIntervalMillis) { refreshRequests.first() }
        }
      }
    }
  }

  override fun refresh() {
    refreshRequests.tryEmit(Unit)
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
    const val CheckIntervalMillis = 10_000L
  }
}
