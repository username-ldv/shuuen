package ldv.shuuen.data.remote.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

internal class LevelSyncApi(private val client: HttpClient) {
  suspend fun sync(
    baseUrl: String,
    accessToken: String,
    request: LevelSyncRequestDto,
  ): LevelSyncEnvelopeDto<LevelSyncResponseDto> =
    client.post {
      url(baseUrl)
      url { appendPathSegments("api", "v1", "sync", "levels") }
      bearerAuth(accessToken)
      contentType(ContentType.Application.Json)
      setBody(request)
    }.body()
}
