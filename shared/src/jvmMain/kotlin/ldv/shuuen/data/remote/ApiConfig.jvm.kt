package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal actual fun defaultApiBaseUrl(): String =
  System.getProperty("shuuen.api.baseUrl")
    ?: System.getenv("SHUUEN_API_BASE_URL")
    ?: "http://127.0.0.1:9999"

internal actual fun createPlatformApiHttpClient(json: Json): HttpClient =
  HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
  }
