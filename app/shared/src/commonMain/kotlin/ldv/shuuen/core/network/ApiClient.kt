package ldv.shuuen.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

/**
 * The common setup of the backend API client:
 * - relative URLs resolve against the backend [config] points at ([BackendUrl]);
 * - JSON goes through [json];
 * - a non-2xx response throws.
 *
 * Authentication is added on top of this by `core/auth` (`installBearerAuth`).
 */
internal fun HttpClientConfig<*>.configureApiClient(json: Json, config: ApiConfig) {
  expectSuccess = true
  install(ContentNegotiation) { json(json) }
  install(BackendUrl) { apiConfig = config }
}

/** The platform's HTTP client: Ktor's Android engine on Android, CIO on desktop. */
internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Resolves relative request URLs (`client.get("api/v1/courses")`) against the configured backend.
 *
 * Ktor's `defaultRequest` can't wait, and the configured URL comes from the settings file, which
 * loads asynchronously. Until it has, a request would go to the platform default instead. This
 * plugin suspends on [ApiConfig.currentBaseUrl] instead. Absolute URLs are left alone.
 */
internal class BackendUrl private constructor(private val config: ApiConfig) {
  class Config {
    lateinit var apiConfig: ApiConfig
  }

  companion object Plugin : HttpClientPlugin<Config, BackendUrl> {
    override val key: AttributeKey<BackendUrl> = AttributeKey("BackendUrl")

    override fun prepare(block: Config.() -> Unit): BackendUrl =
      BackendUrl(Config().apply(block).apiConfig)

    override fun install(plugin: BackendUrl, scope: HttpClient) {
      scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
        val url = context.url
        if (url.host.isNotEmpty()) return@intercept
        val base = Url(plugin.config.currentBaseUrl())
        val path = url.encodedPath.trimStart('/')
        url.protocol = base.protocol
        url.host = base.host
        url.port = base.specifiedPort
        url.encodedPath = base.encodedPath.trimEnd('/') + "/" + path
      }
    }
  }
}
