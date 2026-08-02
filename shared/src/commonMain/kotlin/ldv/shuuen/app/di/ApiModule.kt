package ldv.shuuen.app.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.map
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.online.BackendStatusMonitor
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.data.auth.AuthRepositoryImpl
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.data.remote.KtorBackendStatusMonitor
import ldv.shuuen.data.remote.auth.AuthApi
import ldv.shuuen.data.remote.course.CourseApi
import ldv.shuuen.data.remote.course.CourseDefinitionMapper
import ldv.shuuen.data.remote.course.CourseRepositoryImpl
import ldv.shuuen.data.remote.course.MidiContentResolverImpl
import ldv.shuuen.data.remote.createPlatformApiHttpClient
import ldv.shuuen.data.remote.defaultApiBaseUrl
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.melodies.domain.MidiContentResolver
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val apiModule = module {
  single {
    ApiConfig(
      defaultBaseUrl = defaultApiBaseUrl(),
      configuredBaseUrl = get<SettingsRepository>().settings.map { it.backendUrl },
    )
  }
  single<HttpClient> { createPlatformApiHttpClient(ApiJson) }
  single<CourseApi>()
  single<AuthApi>()
  single<AuthRepositoryImpl>() bind AuthRepository::class
  single<KtorBackendStatusMonitor>() bind BackendStatusMonitor::class
  single { CourseDefinitionMapper(ApiJson) }
  single<CourseRepositoryImpl>() bind CourseRepository::class
  single<MidiContentResolverImpl>() bind MidiContentResolver::class
}
