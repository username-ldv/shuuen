package ldv.shuuen.core

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ldv.shuuen.core.audio.bass.BassMidiEngine
import ldv.shuuen.core.audio.bass.BassMidiFilePlayer
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.auth.AuthApi
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthRepositoryImpl
import ldv.shuuen.core.auth.installBearerAuth
import ldv.shuuen.core.network.ApiConfig
import ldv.shuuen.core.network.ApiJson
import ldv.shuuen.core.network.ApiJsonQualifier
import ldv.shuuen.core.network.BackendStatusMonitor
import ldv.shuuen.core.network.KtorBackendStatusMonitor
import ldv.shuuen.core.network.configureApiClient
import ldv.shuuen.core.network.defaultApiBaseUrl
import ldv.shuuen.core.network.platformHttpClient
import ldv.shuuen.core.settings.KStoreSettingsRepository
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.sync.DataSync
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** Koin bindings of everything in `core`. */
val coreModule = module {
  single<KStoreSettingsRepository>() bind SettingsRepository::class

  single {
    ApiConfig(
      defaultBaseUrl = defaultApiBaseUrl(),
      configuredBaseUrl = get<SettingsRepository>().settings.map { it.backendUrl },
    )
  }
  single<Json>(named(ApiJsonQualifier)) { ApiJson }
  single<HttpClient> {
    // Read lazily: the auth repository needs AuthApi, which needs this client.
    val authRepository by lazy { get<AuthRepository>() }
    platformHttpClient {
      configureApiClient(get(named(ApiJsonQualifier)), get())
      installBearerAuth { authRepository }
    }
  }
  single<KtorBackendStatusMonitor>() bind BackendStatusMonitor::class

  single<AuthApi>()
  single<AuthRepositoryImpl>() bind AuthRepository::class

  // Receives every `bind Synchronizer::class` of the feature modules.
  single<DataSync>()

  single<BassMidiEngine>() bind MidiEngine::class
  single<BassMidiFilePlayer>() bind MidiFilePlayer::class
}
