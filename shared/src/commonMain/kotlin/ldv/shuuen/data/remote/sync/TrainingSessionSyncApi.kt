package ldv.shuuen.data.remote.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import ldv.shuuen.data.remote.bodyAndClose

internal class TrainingSessionSyncApi(private val client: HttpClient) {
  suspend fun sync(
    baseUrl: String,
    accessToken: String,
    request: TrainingSessionSyncRequestDto,
  ): LevelSyncEnvelopeDto<TrainingSessionSyncResponseDto> =
    client.post {
      url(baseUrl)
      url { appendPathSegments("api", "v1", "sync", "training-sessions") }
      bearerAuth(accessToken)
      contentType(ContentType.Application.Json)
      setBody(request)
    }.bodyAndClose()
}
