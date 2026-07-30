package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.online.BackendStatus

@OptIn(ExperimentalCoroutinesApi::class)
class KtorBackendStatusMonitorTest {
  @Test
  fun aTimedOutCheckMarksTheBackendUnavailableAndKeepsMonitoring() = runTest {
    val engine = MockEngine {
      delay(4_000)
      respond("{}")
    }
    val monitor = KtorBackendStatusMonitor(
      client = HttpClient(engine),
      config = ApiConfig("http://backend.test"),
      scope = backgroundScope,
    )

    runCurrent()
    advanceTimeBy(3_001)
    runCurrent()

    assertEquals(BackendStatus.Unavailable, monitor.status.value)
  }
}
