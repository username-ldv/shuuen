package ldv.shuuen.features.training.course

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import ldv.shuuen.core.network.DataEnvelopeDto
import ldv.shuuen.core.network.bodyAndClose
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.level.apiName

/** The public course catalog of the configured backend. */
internal class CourseApi(private val client: HttpClient) {
  suspend fun listCourses(limit: Int, offset: Int): PageEnvelopeDto<CourseDto> =
    client
      .get("api/v1/courses") {
        parameter("limit", limit)
        parameter("offset", offset)
      }
      .bodyAndClose()

  suspend fun getCourse(courseId: Long): DataEnvelopeDto<CourseDto> =
    client.get { url { appendPathSegments(courseSegments(courseId)) } }.bodyAndClose()

  suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): DataEnvelopeDto<CourseModeDto> =
    client
      .get { url { appendPathSegments(courseSegments(courseId) + mode.apiName) } }
      .bodyAndClose()

  suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): PageEnvelopeDto<CourseLevelDto> =
    client
      .get {
        url { appendPathSegments(courseSegments(courseId) + listOf(mode.apiName, "levels")) }
        parameter("group_id", groupId)
        parameter("limit", limit)
        parameter("offset", offset)
      }
      .bodyAndClose()

  suspend fun getLevel(
    courseId: Long,
    mode: TrainingFlow,
    levelId: String,
  ): DataEnvelopeDto<CourseLevelDto> =
    client
      .get {
        url {
          appendPathSegments(courseSegments(courseId) + listOf(mode.apiName, "levels", levelId))
        }
      }
      .bodyAndClose()

  suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): DataEnvelopeDto<List<CourseLevelDto>> =
    client
      .post {
        url {
          appendPathSegments(
            courseSegments(courseId) + listOf(mode.apiName, "levels", "query")
          )
        }
        contentType(ContentType.Application.Json)
        setBody(QueryCourseLevelsRequestDto(levelIds))
      }
      .bodyAndClose()

  /** [pathOrUrl] is either relative to the backend or a full URL elsewhere. */
  suspend fun downloadBytes(pathOrUrl: String): ByteArray = client.get(pathOrUrl).bodyAndClose()

  private fun courseSegments(courseId: Long) = listOf("api", "v1", "courses", courseId.toString())
}
