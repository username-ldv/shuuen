package ldv.shuuen.data.remote.course

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.features.training.melodies.domain.MidiFileSource

class MidiContentResolverImplTest {
  @Test
  fun downloadsBackendMidiOnDemandAndCachesByVariant() = runTest {
    var requests = 0
    val expected = byteArrayOf(0x4d, 0x54, 0x68, 0x64)
    val engine = MockEngine {
      requests += 1
      respond(expected, HttpStatusCode.OK)
    }
    val resolver =
      MidiContentResolverImpl(
        CourseApi(HttpClient(engine), ApiConfig("http://backend.test"))
      )
    val source = MidiFileSource.Backend(
      melodyId = 10,
      variantId = 20,
      fileName = "piece.mid",
      downloadUrl = "/api/v1/library/variants/20/download",
    )

    assertContentEquals(expected, resolver.resolve(source))
    assertContentEquals(expected, resolver.resolve(source))
    assertEquals(1, requests)
  }
}
