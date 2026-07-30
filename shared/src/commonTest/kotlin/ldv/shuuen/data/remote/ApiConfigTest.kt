package ldv.shuuen.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ApiConfigTest {
  @Test
  fun followsConfiguredBackendUrlAndFallsBackToPlatformDefault() = runTest {
    val configured = MutableStateFlow<String?>(null)
    val config = ApiConfig("http://platform.test:9999/", configured)

    assertEquals("http://platform.test:9999", config.currentBaseUrl())

    configured.value = " https://custom.test/api/ "
    assertEquals("https://custom.test/api", config.currentBaseUrl())

    configured.value = null
    assertEquals("http://platform.test:9999", config.currentBaseUrl())
  }

  @Test
  fun rejectsUnsupportedBackendUrlSchemes() {
    assertFailsWith<IllegalArgumentException> {
      normalizeBackendUrl("ftp://backend.test")
    }
  }

  @Test
  fun rejectsBackendUrlsWithoutAHost() {
    assertFailsWith<IllegalArgumentException> {
      normalizeBackendUrl("http://")
    }
  }
}
