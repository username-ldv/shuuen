package ldv.shuuen.core.auth

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

/**
 * Signs requests to the signed-in backend with the session's token.
 *
 * - The token is read from [authRepository] on every request (`cacheTokens = false`), so signing
 *   in, signing out and renewals take effect at once.
 * - A token belongs to the backend that issued it, so only requests to that backend carry it.
 * - A 401 renews the token once and retries the request; a rejected renewal signs out.
 *
 * [authRepository] is read lazily: the repository needs `AuthApi`, which needs this client.
 */
internal fun HttpClientConfig<*>.installBearerAuth(authRepository: () -> AuthRepository) {
  install(Auth) {
    bearer {
      cacheTokens = false
      loadTokens { authRepository().session.value?.let { BearerTokens(it.accessToken, null) } }
      refreshTokens {
        authRepository().renewSession()?.let { BearerTokens(it.accessToken, null) }
      }
      sendWithoutRequest { request ->
        val backendUrl = authRepository().session.value?.backendUrl
        backendUrl != null &&
          !request.attributes.contains(AuthCircuitBreaker) &&
          request.url.buildString().let { it == backendUrl || it.startsWith("$backendUrl/") }
      }
    }
  }
}
