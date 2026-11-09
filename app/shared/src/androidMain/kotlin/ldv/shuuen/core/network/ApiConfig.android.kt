package ldv.shuuen.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android

internal actual fun defaultApiBaseUrl(): String = "https://shuuen.xyz"

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
  HttpClient(Android, block)
