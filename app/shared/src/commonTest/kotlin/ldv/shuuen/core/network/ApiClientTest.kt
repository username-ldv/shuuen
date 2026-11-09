package ldv.shuuen.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientTest {
  @Test
  fun relativeRequestsGoToTheConfiguredBackend() = runTest {
    val requested = mutableListOf<String>()
    val configured = MutableStateFlow<String?>("https://custom.test/prefix")
    val client = client(ApiConfig("http://platform.test", configured)) { requested += it }

    client.get("api/v1/courses")
    client.get("/api/v1/library/variants/20/download")
    client.get { url { appendPathSegments("api", "v1", "courses", "7") } }
    configured.value = null
    client.get("api/v1/courses?limit=1")

    assertEquals(
      listOf(
        "https://custom.test/prefix/api/v1/courses",
        "https://custom.test/prefix/api/v1/library/variants/20/download",
        "https://custom.test/prefix/api/v1/courses/7",
        "http://platform.test/api/v1/courses?limit=1",
      ),
      requested,
    )
  }

  @Test
  fun absoluteUrlsAreLeftAlone() = runTest {
    val requested = mutableListOf<String>()
    val client = client(ApiConfig("http://platform.test")) { requested += it }

    client.get("https://cdn.test/file.mid")

    assertEquals(listOf("https://cdn.test/file.mid"), requested)
  }

  @Test
  fun requestsWaitUntilTheSettingsHaveLoaded() = runTest {
    val requested = mutableListOf<String>()
    val loaded = MutableStateFlow(false)
    // Like the settings file: nothing is emitted until it has been read.
    val config =
      ApiConfig("http://platform.test", loaded.filter { it }.map { "https://custom.test" })
    val client = client(config) { requested += it }

    val response = async { client.get("api/v1/courses") }
    runCurrent()
    assertEquals(emptyList(), requested)

    loaded.value = true
    response.await()
    assertEquals(listOf("https://custom.test/api/v1/courses"), requested)
  }

  private fun client(config: ApiConfig, onRequest: (String) -> Unit) =
    HttpClient(
      MockEngine { request ->
        onRequest(request.url.toString())
        respond("{}")
      }
    ) {
      configureApiClient(ApiJson, config)
    }
}
