package ldv.shuuen.data.remote.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import ldv.shuuen.data.remote.bodyAndClose

/**
 * Each call takes the backend URL explicitly: a session belongs to the backend that issued it, and
 * the caller has already resolved which one that is.
 */
internal class AuthApi(private val client: HttpClient) {
  suspend fun login(
    baseUrl: String,
    username: String,
    password: String,
  ): AuthEnvelopeDto<AuthResultDto> =
    client.post {
      url(baseUrl)
      url { appendPathSegments("api", "v1", "auth", "login") }
      contentType(ContentType.Application.Json)
      setBody(LoginRequestDto(username, password))
    }.bodyAndClose()

  /** Trades a still-valid token for a fresh one, which also re-reads the account. */
  suspend fun refresh(baseUrl: String, accessToken: String): AuthEnvelopeDto<AuthResultDto> =
    client.post {
      url(baseUrl)
      url { appendPathSegments("api", "v1", "auth", "refresh") }
      bearerAuth(accessToken)
    }.bodyAndClose()
}
