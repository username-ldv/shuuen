package ldv.shuuen.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ldv.shuuen.core.network.DataEnvelopeDto
import ldv.shuuen.core.network.bodyAndClose

/** Both calls go to the backend currently configured, outside of the client's bearer handling. */
internal class AuthApi(private val client: HttpClient) {
  suspend fun login(username: String, password: String): DataEnvelopeDto<AuthResultDto> =
    client
      .post("api/v1/auth/login") {
        bypassBearerAuth()
        contentType(ContentType.Application.Json)
        setBody(LoginRequestDto(username, password))
      }
      .bodyAndClose()

  /** Trades a still-valid token for a fresh one, which also re-reads the account. */
  suspend fun refresh(accessToken: String): DataEnvelopeDto<AuthResultDto> =
    client
      .post("api/v1/auth/refresh") {
        bypassBearerAuth()
        bearerAuth(accessToken)
      }
      .bodyAndClose()
}

/**
 * Keeps Ktor's `Auth` plugin away from this request: it neither attaches the session's token nor
 * tries to renew the token when the response is a 401. Login has no token yet, a rejected password
 * is no reason to renew one, and a refresh presents the token it renews itself.
 */
internal fun HttpRequestBuilder.bypassBearerAuth() {
  attributes.put(AuthCircuitBreaker, Unit)
}
