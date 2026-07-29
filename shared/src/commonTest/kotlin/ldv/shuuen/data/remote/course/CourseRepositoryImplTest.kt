package ldv.shuuen.data.remote.course

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.features.training.common.TrainingFlow

class CourseRepositoryImplTest {
  @Test
  fun decodesCourseDiscoveryAndOrderedProgressionGroups() = runTest {
    val engine = MockEngine { request ->
      val body =
        if (request.url.encodedPath.endsWith("/melodies")) modeResponse
        else coursePageResponse
      respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = HttpClient(engine) {
      expectSuccess = true
      install(ContentNegotiation) { json(ApiJson) }
    }
    val repository =
      CourseRepositoryImpl(
        CourseApi(client, ApiConfig("http://backend.test")),
        CourseDefinitionMapper(ApiJson),
      )

    val page = repository.listCourses(limit = 50, offset = 0)
    val mode = repository.getCourseMode(7, TrainingFlow.Melodies)

    assertEquals("C tonic", page.courses.single().name)
    assertEquals(66, page.courses.single().modes.single().levelCount)
    assertEquals(listOf("C major", "F♯"), mode.groups.map { it.name })
    assertEquals(listOf(0, 1), mode.groups.map { it.sortOrder })
  }

  @Test
  fun postsLevelQueriesToThePublicQueryEndpoint() = runTest {
    var requestBody = ""
    val engine = MockEngine { request ->
      assertTrue(request.url.encodedPath.endsWith("/api/v1/courses/7/melodies/levels/query"))
      requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
      respond(
        content = "{\"data\":[]}",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
      )
    }
    val client = HttpClient(engine) {
      expectSuccess = true
      install(ContentNegotiation) { json(ApiJson) }
    }
    val repository = CourseRepositoryImpl(
      CourseApi(client, ApiConfig("http://backend.test")),
      CourseDefinitionMapper(ApiJson),
    )

    val result = repository.queryLevels(7, TrainingFlow.Melodies, listOf("a", "b"))

    assertTrue(result.levels.isEmpty())
    assertTrue(requestBody.contains("\"ids\":[\"a\",\"b\"]"))
  }
}

private val coursePageResponse =
  """
  {"data":[{
    "id":7,"library_group_id":7,"slug":"c-tonic","name":"C tonic","description":"Course",
    "author":"","is_public":true,"sort_order":0,"structure_source":"managed","mode_count":1,
    "progression_group_count":6,"level_count":66,
    "modes":[{"mode":"melodies","name":"Melodies","description":"","sort_order":0,
      "group_count":6,"level_count":66}]
  }],"meta":{"limit":50,"offset":0,"total":1}}
  """.trimIndent()

private val modeResponse =
  """
  {"data":{"mode":"melodies","name":"Melodies","description":"","sort_order":0,
    "group_count":2,"level_count":22,"groups":[
      {"id":"g1","library_group_id":1,"name":"C major","description":"","sort_order":0,
        "level_count":11,"section_count":0,"blueprint":false},
      {"id":"g2","library_group_id":2,"name":"F♯","description":"","sort_order":1,
        "level_count":11,"section_count":0,"blueprint":false}
    ]}}
  """.trimIndent()
