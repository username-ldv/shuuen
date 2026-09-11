package ldv.shuuen.core.auth

import kotlinx.coroutines.flow.StateFlow

/** The signed-in account as the backend describes it. */
data class AuthUser(
  val id: Long,
  val username: String,
  val displayName: String,
  val role: String,
) {
  val isAdmin: Boolean
    get() = role == AdminRole

  /** What the UI shows: the display name when the account has one, the username otherwise. */
  val label: String
    get() = displayName.ifBlank { username }

  private companion object {
    const val AdminRole = "admin"
  }
}

/**
 * A signed-in backend account. [accessToken] is a bearer token for [backendUrl] only — a session
 * issued by one backend means nothing on another, so both travel together.
 */
data class AuthSession(
  val user: AuthUser,
  val accessToken: String,
  val backendUrl: String,
)

enum class AuthFailure {
  /** The backend rejected the username/password pair, or refused the request as malformed. */
  InvalidCredentials,

  /** The backend could not be reached at all. */
  Unreachable,

  /** Too many attempts from this address; the backend rate-limits authentication. */
  RateLimited,

  Server,
}

class AuthException(
  val failure: AuthFailure,
  override val message: String,
  override val cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The optional backend account. Everything in the app works signed out; a session only adds
 * whatever the backend puts behind an account, so failures here never block training.
 */
interface AuthRepository {
  /** The current session, or null while signed out. Restored from disk when the app starts. */
  val session: StateFlow<AuthSession?>

  /** Signs in against the currently configured backend, throwing [AuthException] on failure. */
  suspend fun signIn(username: String, password: String): AuthSession

  /** Forgets the stored session. The backend issues stateless tokens, so nothing is revoked. */
  suspend fun signOut()
}
