package ldv.shuuen.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.network.ApiConfig
import ldv.shuuen.core.network.ApiJson
import ldv.shuuen.core.network.configureApiClient

class BearerAuthTest {
  @Test
  fun requestsToTheSignedInBackendCarryTheSessionToken() = runTest {
    val auth = FakeAuthRepository(session("token-1"))
    val presented = mutableListOf<String?>()
    val client = client(auth) { presented += it.headers[HttpHeaders.Authorization]; ok() }

    client.get("api/v1/sync/levels")
    auth.session.value = session("token-2")
    client.get("api/v1/sync/levels")

    assertEquals(listOf<String?>("Bearer token-1", "Bearer token-2"), presented)
  }

  @Test
  fun otherHostsAndSignedOutRequestsCarryNoToken() = runTest {
    val auth = FakeAuthRepository(session("token"))
    val presented = mutableListOf<String?>()
    val client = client(auth) { presented += it.headers[HttpHeaders.Authorization]; ok() }

    client.get("https://backend.test.example/api/v1/courses")
    client.get("https://cdn.test/file.mid")
    auth.session.value = null
    client.get("api/v1/courses")

    assertEquals(listOf<String?>(null, null, null), presented)
  }

  @Test
  fun aRejectedTokenIsRenewedOnceAndTheRequestRetried() = runTest {
    val auth = FakeAuthRepository(session("expired")) { session("fresh") }
    val presented = mutableListOf<String?>()
    val client =
      client(auth) { request ->
        val token = request.headers[HttpHeaders.Authorization]
        presented += token
        if (token == "Bearer fresh") ok() else respondError(HttpStatusCode.Unauthorized)
      }

    client.get("api/v1/sync/levels")

    assertEquals(listOf<String?>("Bearer expired", "Bearer fresh"), presented)
    assertEquals(1, auth.renewals)
  }

  @Test
  fun loginAndRefreshDontUseTheSessionToken() = runTest {
    val auth = FakeAuthRepository(session("session-token"))
    val presented = mutableListOf<String?>()
    val client =
      client(auth) { request ->
        presented += request.headers[HttpHeaders.Authorization]
        if (request.url.encodedPath.endsWith("/login")) {
          respondError(HttpStatusCode.Unauthorized)
        } else {
          respond(
            AuthResult,
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
          )
        }
      }
    val api = AuthApi(client)

    runCatching { api.login("learner", "wrong") }
    api.refresh("old-token")

    assertEquals(listOf<String?>(null, "Bearer old-token"), presented)
    assertEquals(0, auth.renewals)
  }

  private fun client(
    auth: AuthRepository,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
  ) =
    HttpClient(MockEngine { request -> handler(request) }) {
      configureApiClient(ApiJson, ApiConfig(BackendUrl))
      installBearerAuth { auth }
    }

  private fun MockRequestHandleScope.ok() = respond("{}")

  private fun session(token: String) =
    AuthSession(AuthUser(7, "learner", "", "user"), accessToken = token, backendUrl = BackendUrl)

  private class FakeAuthRepository(
    initial: AuthSession?,
    private val renewed: (() -> AuthSession)? = null,
  ) : AuthRepository {
    override val session = MutableStateFlow(initial)
    var renewals = 0

    override suspend fun signIn(username: String, password: String): AuthSession = error("unused")

    override suspend fun signOut() {
      session.value = null
    }

    override suspend fun renewSession(): AuthSession? {
      renewals += 1
      return renewed?.invoke()?.also { session.value = it }
    }
  }

  private companion object {
    const val BackendUrl = "https://backend.test"

    val AuthResult =
      """
      {"data":{"user":{"id":7,"username":"learner","display_name":"","role":"user"},
      "access_token":"new-token","token_type":"Bearer"}}
      """
        .trimIndent()
  }
}
