package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.online.BackendStatus

@OptIn(ExperimentalCoroutinesApi::class)
class KtorBackendStatusMonitorTest {
  @Test
  fun aTimedOutManualCheckMarksTheBackendUnavailable() = runTest {
    val engine = MockEngine {
      delay(4_000)
      respond("{}")
    }
    val monitor = KtorBackendStatusMonitor(
      client = HttpClient(engine),
      config = ApiConfig("http://backend.test"),
      scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )
    monitor.refresh()

    runCurrent()
    advanceTimeBy(3_001)
    runCurrent()

    assertEquals(BackendStatus.Unavailable, monitor.status.value)
  }

  @Test
  fun doesNotCheckUntilExplicitlyRefreshed() = runTest {
    var requestCount = 0
    val requestReceived = CompletableDeferred<Unit>()
    val engine = MockEngine {
      requestCount += 1
      requestReceived.complete(Unit)
      respond("{}")
    }
    val monitor = KtorBackendStatusMonitor(
      client = HttpClient(engine),
      config = ApiConfig("http://backend.test"),
    )

    runCurrent()
    advanceTimeBy(30_000)
    runCurrent()
    assertEquals(0, requestCount)

    monitor.refresh()
    requestReceived.await()
    assertEquals(1, requestCount)
  }
}
