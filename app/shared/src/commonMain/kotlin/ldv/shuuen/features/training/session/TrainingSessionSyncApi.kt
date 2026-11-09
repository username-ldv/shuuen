package ldv.shuuen.features.training.session

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ldv.shuuen.core.network.DataEnvelopeDto
import ldv.shuuen.core.network.bodyAndClose

/** Authenticated by the client's bearer handling, so it needs a signed-in session. */
internal class TrainingSessionSyncApi(private val client: HttpClient) {
  suspend fun sync(
    request: TrainingSessionSyncRequestDto
  ): DataEnvelopeDto<TrainingSessionSyncResponseDto> =
    client
      .post("api/v1/sync/training-sessions") {
        contentType(ContentType.Application.Json)
        setBody(request)
      }
      .bodyAndClose()
}
