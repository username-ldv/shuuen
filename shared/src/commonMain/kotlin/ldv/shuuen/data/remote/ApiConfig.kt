package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class ApiConfig(
  defaultBaseUrl: String,
  configuredBaseUrl: Flow<String?> = flowOf(null),
) {
  val normalizedDefaultBaseUrl: String =
    requireNotNull(normalizeBackendUrl(defaultBaseUrl)) { "The default backend URL is blank." }

  val baseUrl: Flow<String> = configuredBaseUrl
    .map(::effectiveBaseUrl)
    .distinctUntilChanged()

  fun effectiveBaseUrl(configured: String?): String =
    normalizeBackendUrl(configured) ?: normalizedDefaultBaseUrl

  suspend fun currentBaseUrl(): String = baseUrl.first()

  suspend fun resolve(pathOrUrl: String): String =
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
      pathOrUrl
    } else {
      "${currentBaseUrl()}/${pathOrUrl.trimStart('/')}"
    }
}

/** Trims a user-entered base URL, or returns null when the platform default should be used. */
fun normalizeBackendUrl(value: String?): String? {
  val normalized = value?.trim()?.trimEnd('/').orEmpty()
  if (normalized.isEmpty()) return null
  require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
    "Enter a URL beginning with http:// or https://."
  }
  val authority = normalized.substringAfter("://").substringBefore('/')
  require(authority.isNotBlank() && authority.none(Char::isWhitespace)) {
    "Enter a valid backend URL."
  }
  return normalized
}

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
  ignoreUnknownKeys = true
  classDiscriminator = "type"
  namingStrategy = JsonNamingStrategy.SnakeCase
}

/** Koin qualifier for the API's snake-case JSON format. */
internal const val ApiJsonQualifier = "apiJson"

internal expect fun defaultApiBaseUrl(): String

internal expect fun createPlatformApiHttpClient(json: Json): HttpClient
