package ldv.shuuen.data.remote.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

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
    }.body()

  suspend fun me(baseUrl: String, accessToken: String): AuthEnvelopeDto<AuthUserDto> =
    client.get {
      url(baseUrl)
      url { appendPathSegments("api", "v1", "auth", "me") }
      bearerAuth(accessToken)
    }.body()
}
