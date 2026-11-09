package ldv.shuuen.core.network

import io.ktor.client.plugins.ResponseException
import kotlinx.serialization.Serializable

/** The backend's envelope around a single result: `{"data": ...}`. */
@Serializable internal data class DataEnvelopeDto<T>(val data: T)

/** The backend's error envelope: `{"error": "...", "details": ...}`. */
@Serializable internal data class ApiErrorDto(val error: String = "")

/** The backend's own explanation of this failed response, or null when it gave none. */
internal suspend fun ResponseException.apiErrorMessage(): String? =
  runCatching { response.bodyAndClose<ApiErrorDto>().error }
    .getOrNull()
    ?.takeIf { it.isNotBlank() }
