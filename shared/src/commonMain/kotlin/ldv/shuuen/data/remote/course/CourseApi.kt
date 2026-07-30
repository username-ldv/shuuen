package ldv.shuuen.data.remote.course

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.appendPathSegments
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.apiName

internal class CourseApi(
  private val client: HttpClient,
  private val config: ApiConfig,
) {
  suspend fun listCourses(limit: Int, offset: Int): PageEnvelopeDto<CourseDto> =
    client.get {
      url(config.currentBaseUrl())
      url {
        appendPathSegments("api", "v1", "courses")
        parameters.append("limit", limit.toString())
        parameters.append("offset", offset.toString())
      }
    }.body()

  suspend fun getCourse(courseId: Long): DataEnvelopeDto<CourseDto> =
    client.get {
      url(config.currentBaseUrl())
      url { appendPathSegments("api", "v1", "courses", courseId.toString()) }
    }.body()

  suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): DataEnvelopeDto<CourseModeDto> =
    client.get {
      url(config.currentBaseUrl())
      url { appendPathSegments("api", "v1", "courses", courseId.toString(), mode.apiName) }
    }.body()

  suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): PageEnvelopeDto<CourseLevelDto> =
    client.get {
      url(config.currentBaseUrl())
      url {
        appendPathSegments("api", "v1", "courses", courseId.toString(), mode.apiName, "levels")
        parameters.append("group_id", groupId)
        parameters.append("limit", limit.toString())
        parameters.append("offset", offset.toString())
      }
    }.body()

  suspend fun getLevel(
    courseId: Long,
    mode: TrainingFlow,
    levelId: String,
  ): DataEnvelopeDto<CourseLevelDto> =
    client.get {
      url(config.currentBaseUrl())
      url {
        appendPathSegments(
          "api",
          "v1",
          "courses",
          courseId.toString(),
          mode.apiName,
          "levels",
          levelId,
        )
      }
    }.body()

  suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): DataEnvelopeDto<List<CourseLevelDto>> =
    client.post {
      url(config.currentBaseUrl())
      url {
        appendPathSegments(
          "api",
          "v1",
          "courses",
          courseId.toString(),
          mode.apiName,
          "levels",
          "query",
        )
      }
      contentType(ContentType.Application.Json)
      setBody(QueryCourseLevelsRequestDto(levelIds))
    }.body()

  suspend fun downloadBytes(pathOrUrl: String): ByteArray =
    client.get(config.resolve(pathOrUrl)).body()
}
