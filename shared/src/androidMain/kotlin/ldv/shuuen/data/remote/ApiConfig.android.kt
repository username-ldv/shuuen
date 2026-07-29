package ldv.shuuen.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal actual fun defaultApiBaseUrl(): String = "http://10.0.2.2:9999"

internal actual fun createPlatformApiHttpClient(json: Json): HttpClient =
  HttpClient(Android) {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
  }
