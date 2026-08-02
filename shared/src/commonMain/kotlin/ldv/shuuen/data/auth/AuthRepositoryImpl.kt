package ldv.shuuen.data.auth

import io.github.aakira.napier.Napier
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import ldv.shuuen.core.auth.AuthException
import ldv.shuuen.core.auth.AuthFailure
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.auth.AuthUser
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.bodyAndClose
import ldv.shuuen.data.remote.auth.ApiErrorDto
import ldv.shuuen.data.remote.auth.AuthApi
import ldv.shuuen.data.remote.auth.AuthUserDto
import org.koin.core.annotation.Named

/** The on-disk shape of a session. Kept private to this file so the domain model can change. */
@Serializable
internal data class StoredAuthSession(
  val backendUrl: String,
  val accessToken: String,
  val user: StoredAuthUser,
)

@Serializable
internal data class StoredAuthUser(
  val id: Long,
  val username: String,
  val displayName: String,
  val role: String,
)

/**
 * Signs in against the configured backend and remembers the session across launches.
 *
 * The token is stored beside the app's other data in plain text — it is a low-value credential for
 * an optional, self-hosted backend, and neither target platform gives Compose Multiplatform a
 * shared secure-storage API.
 */
internal class AuthRepositoryImpl(
  @Named("files") path: Path,
  private val api: AuthApi,
  private val config: ApiConfig,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthRepository {
  private val store = storeOf<StoredAuthSession>(file = Path(path, "session.json"))
  private val mutableSession = MutableStateFlow<AuthSession?>(null)
  override val session = mutableSession.asStateFlow()

  init {
    // The configured backend decides which session is valid, so restoring and re-checking hang off
    // that URL: the first emission restores at startup, later ones mean the user switched backends.
    scope.launch {
      config.baseUrl.collectLatest(::adoptStoredSessionFor)
    }
  }

  override suspend fun signIn(username: String, password: String): AuthSession {
    val trimmedUsername = username.trim()
    if (trimmedUsername.isEmpty() || password.isEmpty()) {
      throw AuthException(AuthFailure.InvalidCredentials, "Enter your username and password.")
    }

    val baseUrl = config.currentBaseUrl()
    val result =
      try {
        api.login(baseUrl, trimmedUsername, password).data
      } catch (error: CancellationException) {
        throw error
      } catch (error: ResponseException) {
        throw error.toAuthException()
      } catch (error: Throwable) {
        throw AuthException(AuthFailure.Unreachable, "Couldn't reach $baseUrl.", error)
      }

    val stored = StoredAuthSession(baseUrl, result.accessToken, result.user.toStored())
    persist(stored)
    return stored.toSession()
  }

  override suspend fun signOut() {
    clear()
  }

  /** Publishes the stored session when it belongs to [baseUrl], and drops it when it doesn't. */
  private suspend fun adoptStoredSessionFor(baseUrl: String) {
    val stored = readStore()
    when {
      stored == null -> mutableSession.value = null
      stored.backendUrl != baseUrl -> clear()
      else -> {
        mutableSession.value = stored.toSession()
        verify(stored)
      }
    }
  }

  /**
   * Drops a session the backend no longer honours. Only an explicit rejection signs the user out —
   * an unreachable backend leaves the session in place so restarting offline stays signed in.
   */
  private suspend fun verify(stored: StoredAuthSession) {
    try {
      val user = api.me(stored.backendUrl, stored.accessToken).data
      persist(stored.copy(user = user.toStored()))
    } catch (error: CancellationException) {
      throw error
    } catch (error: ResponseException) {
      if (error.response.status == HttpStatusCode.Unauthorized) {
        Napier.i { "The stored backend session was rejected; signing out." }
        clear()
      }
    } catch (error: Throwable) {
      Napier.v(error) { "Couldn't verify the stored backend session." }
    }
  }

  private suspend fun persist(stored: StoredAuthSession) {
    runCatching { store.set(stored) }
      .onFailure { Napier.w(it) { "Couldn't save the backend session." } }
    mutableSession.value = stored.toSession()
  }

  private suspend fun clear() {
    runCatching { store.delete() }
      .onFailure { Napier.w(it) { "Couldn't delete the stored backend session." } }
    mutableSession.value = null
  }

  private suspend fun readStore(): StoredAuthSession? =
    runCatching { store.get() }
      .onFailure { Napier.w(it) { "Couldn't read the stored backend session." } }
      .getOrNull()

  private suspend fun ResponseException.toAuthException(): AuthException {
    val serverMessage =
      runCatching { response.bodyAndClose<ApiErrorDto>().error }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
    return when (response.status) {
      HttpStatusCode.Unauthorized ->
        AuthException(AuthFailure.InvalidCredentials, "Wrong username or password.", this)
      HttpStatusCode.BadRequest ->
        AuthException(
          AuthFailure.InvalidCredentials,
          serverMessage ?: "The backend rejected those details.",
          this,
        )
      HttpStatusCode.TooManyRequests ->
        AuthException(
          AuthFailure.RateLimited,
          "Too many sign-in attempts. Wait a moment and try again.",
          this,
        )
      else ->
        AuthException(AuthFailure.Server, serverMessage ?: "The backend couldn't sign you in.", this)
    }
  }
}

private fun AuthUserDto.toStored() = StoredAuthUser(id, username, displayName, role)

private fun StoredAuthSession.toSession() =
  AuthSession(
    user = AuthUser(user.id, user.username, user.displayName, user.role),
    accessToken = accessToken,
    backendUrl = backendUrl,
  )
