package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

data class ApiConfig(val baseUrl: String) {
  init {
    require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
      "The API base URL must use HTTP or HTTPS."
    }
  }

  val normalizedBaseUrl: String = baseUrl.trimEnd('/')

  fun resolve(pathOrUrl: String): String =
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
      pathOrUrl
    } else {
      "$normalizedBaseUrl/${pathOrUrl.trimStart('/')}"
    }
}

@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
  ignoreUnknownKeys = true
  classDiscriminator = "type"
  namingStrategy = JsonNamingStrategy.SnakeCase
}

internal expect fun defaultApiBaseUrl(): String

internal expect fun createPlatformApiHttpClient(json: Json): HttpClient
