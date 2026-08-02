package ldv.shuuen.data.remote.auth

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthEnvelopeDto<T>(val data: T)

@Serializable
internal data class LoginRequestDto(val username: String, val password: String)

@Serializable
internal data class AuthResultDto(
  val user: AuthUserDto,
  val accessToken: String,
  val tokenType: String = "Bearer",
  val expiresAt: String? = null,
)

@Serializable
internal data class AuthUserDto(
  val id: Long,
  val username: String,
  val displayName: String = "",
  val role: String = "user",
)

/** The backend's error envelope: `{"error": "...", "details": ...}`. */
@Serializable
internal data class ApiErrorDto(val error: String = "")
