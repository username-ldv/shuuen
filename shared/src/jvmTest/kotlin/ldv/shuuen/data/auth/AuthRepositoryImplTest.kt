package ldv.shuuen.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import ldv.shuuen.core.auth.AuthException
import ldv.shuuen.core.auth.AuthFailure
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.data.remote.auth.AuthApi

/**
 * These touch the real filesystem through KStore, which runs off the test scheduler, so they use
 * [runBlocking] and bounded waits rather than virtual time.
 */
class AuthRepositoryImplTest {
  private val directory = createTempDirectory("shuuen-auth-test")
  private val storePath = Path(directory.toString())
  private val scopes = mutableListOf<CoroutineScope>()

  @AfterTest
  fun tearDown() {
    scopes.forEach { it.cancel() }
    directory.toFile().deleteRecursively()
  }

  @Test
  fun signingInStoresTheSessionForTheBackendThatIssuedIt() = runBlocking {
    val repository = repository(engine = authEngine())

    val session = repository.signIn("  Learner  ", "hunter2000")

    assertEquals("Learner", session.user.username)
    assertEquals("Ada", session.user.label)
    assertEquals("issued-token", session.accessToken)
    assertEquals(BackendUrl, session.backendUrl)
    assertEquals(session, repository.session.value)

    val restored = repository(engine = authEngine()).session.await { it != null }
    assertEquals("Learner", assertNotNull(restored).user.username)
  }

  @Test
  fun theLoginRequestSendsTheTrimmedUsernameAndPassword() = runBlocking {
    var body: String? = null
    val repository = repository(
      engine = authEngine(login = { scope, request ->
        body = request.body.toByteArray().decodeToString()
        scope.jsonRespond(LoginResponse)
      }),
    )

    repository.signIn(" Learner ", "hunter2000")

    val sent = assertNotNull(body)
    assertTrue(sent.contains("\"username\":\"Learner\""), sent)
    assertTrue(sent.contains("\"password\":\"hunter2000\""), sent)
  }

  @Test
  fun wrongCredentialsSurfaceAFriendlyMessage() = runBlocking {
    val repository = repository(
      engine = authEngine(login = { scope, _ ->
        scope.jsonRespond(
          """{"error":"invalid username or password"}""",
          HttpStatusCode.Unauthorized,
        )
      }),
    )

    val error = assertFailsWith<AuthException> { repository.signIn("Learner", "wrong-password") }

    assertEquals(AuthFailure.InvalidCredentials, error.failure)
    assertEquals("Wrong username or password.", error.message)
    assertNull(repository.session.value)
  }

  @Test
  fun rateLimitedAttemptsAreReportedSeparately() = runBlocking {
    val repository = repository(
      engine = authEngine(login = { scope, _ ->
        scope.jsonRespond(
          """{"error":"too many authentication attempts"}""",
          HttpStatusCode.TooManyRequests,
        )
      }),
    )

    val error = assertFailsWith<AuthException> { repository.signIn("Learner", "hunter2000") }

    assertEquals(AuthFailure.RateLimited, error.failure)
  }

  @Test
  fun anUnreachableBackendIsNotACredentialProblem() = runBlocking {
    val repository = repository(engine = MockEngine { throw IOException("offline") })

    val error = assertFailsWith<AuthException> { repository.signIn("Learner", "hunter2000") }

    assertEquals(AuthFailure.Unreachable, error.failure)
  }

  @Test
  fun aRejectedStoredTokenSignsTheUserOut() = runBlocking {
    repository(engine = authEngine()).signIn("Learner", "hunter2000")

    val restarted = repository(
      engine = authEngine(me = { scope, _ ->
        scope.jsonRespond("""{"error":"token has been revoked"}""", HttpStatusCode.Unauthorized)
      }),
    )

    assertNull(restarted.session.await { it == null })
  }

  @Test
  fun anUnreachableBackendKeepsTheStoredSession() = runBlocking {
    repository(engine = authEngine()).signIn("Learner", "hunter2000")

    val restarted = repository(
      engine = authEngine(me = { _, _ -> throw IOException("offline") }),
    )

    val session = restarted.session.await { it != null }
    assertEquals("Learner", assertNotNull(session).user.username)
  }

  @Test
  fun aSessionIsDroppedWhenTheConfiguredBackendChanges() = runBlocking {
    repository(engine = authEngine()).signIn("Learner", "hunter2000")

    val configured = MutableStateFlow<String?>(BackendUrl)
    val restarted = repository(engine = authEngine(), config = ApiConfig(BackendUrl, configured))
    assertNotNull(restarted.session.await { it != null })

    configured.value = "http://elsewhere.test"

    assertNull(restarted.session.await { it == null })
  }

  @Test
  fun signingOutForgetsTheStoredSession() = runBlocking {
    val repository = repository(engine = authEngine())
    repository.signIn("Learner", "hunter2000")
    assertTrue(directory.resolve("session.json").exists())

    repository.signOut()

    assertNull(repository.session.value)
    assertFalse(directory.resolve("session.json").exists())
  }

  private fun repository(
    engine: MockEngine,
    config: ApiConfig = ApiConfig(BackendUrl),
  ): AuthRepositoryImpl {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scopes += scope
    val client = HttpClient(engine) {
      expectSuccess = true
      install(ContentNegotiation) { json(ApiJson) }
    }
    return AuthRepositoryImpl(storePath, AuthApi(client), config, scope)
  }

  private companion object {
    const val BackendUrl = "http://backend.test"

    val LoginResponse =
      """
      {"data":{"user":{"id":7,"username":"Learner","display_name":"Ada","role":"user"},
      "access_token":"issued-token","token_type":"Bearer",
      "expires_at":"2026-08-03T10:00:00Z"}}
      """.trimIndent()

    val MeResponse =
      """{"data":{"id":7,"username":"Learner","display_name":"Ada","role":"user"}}"""

    fun MockRequestHandleScope.jsonRespond(
      body: String,
      status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData =
      respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
      )

    fun authEngine(
      login: suspend (MockRequestHandleScope, HttpRequestData) -> HttpResponseData = { scope, _ ->
        scope.jsonRespond(LoginResponse)
      },
      me: suspend (MockRequestHandleScope, HttpRequestData) -> HttpResponseData = { scope, _ ->
        scope.jsonRespond(MeResponse)
      },
    ) = MockEngine { request ->
      when (request.url.encodedPath) {
        "/api/v1/auth/login" -> login(this, request)
        "/api/v1/auth/me" -> me(this, request)
        else -> respondError(HttpStatusCode.NotFound)
      }
    }

    /** Waits for the first value matching [predicate]; the flow is fed by background IO. */
    suspend fun StateFlow<AuthSession?>.await(
      predicate: (AuthSession?) -> Boolean,
    ): AuthSession? = withTimeout(5_000) { first(predicate) }
  }
}
