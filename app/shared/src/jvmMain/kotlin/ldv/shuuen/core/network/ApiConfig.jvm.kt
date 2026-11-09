package ldv.shuuen.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

internal actual fun defaultApiBaseUrl(): String =
  System.getProperty("shuuen.api.baseUrl")
    ?: System.getenv("SHUUEN_API_BASE_URL")
    ?: "http://127.0.0.1:9999"

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
  HttpClient(CIO, block)
