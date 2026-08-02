package ldv.shuuen.app.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.online.BackendStatusMonitor
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.sync.LevelSyncRepository
import ldv.shuuen.core.sync.TrainingSessionSyncRepository
import ldv.shuuen.data.auth.AuthRepositoryImpl
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.data.remote.ApiJsonQualifier
import ldv.shuuen.data.remote.KtorBackendStatusMonitor
import ldv.shuuen.data.remote.auth.AuthApi
import ldv.shuuen.data.remote.course.CourseApi
import ldv.shuuen.data.remote.course.CourseDefinitionMapper
import ldv.shuuen.data.remote.course.CourseRepositoryImpl
import ldv.shuuen.data.remote.course.MidiContentResolverImpl
import ldv.shuuen.data.remote.course.LevelDefinitionCodec
import ldv.shuuen.data.remote.sync.LevelSyncApi
import ldv.shuuen.data.remote.sync.LevelSyncRepositoryImpl
import ldv.shuuen.data.remote.sync.TrainingSessionSyncApi
import ldv.shuuen.data.remote.sync.TrainingSessionSyncRepositoryImpl
import ldv.shuuen.data.remote.createPlatformApiHttpClient
import ldv.shuuen.data.remote.defaultApiBaseUrl
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.melodies.domain.MidiContentResolver
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.core.qualifier.named
import org.koin.plugin.module.dsl.single

val apiModule = module {
  single {
    ApiConfig(
      defaultBaseUrl = defaultApiBaseUrl(),
      configuredBaseUrl = get<SettingsRepository>().settings.map { it.backendUrl },
    )
  }
  single<Json>(named(ApiJsonQualifier)) { ApiJson }
  single<HttpClient> {
    createPlatformApiHttpClient(get(named(ApiJsonQualifier)))
  }
  single<CourseApi>()
  single<AuthApi>()
  single<LevelSyncApi>()
  single<TrainingSessionSyncApi>()
  single<AuthRepositoryImpl>() bind AuthRepository::class
  single<LevelDefinitionCodec>()
  single<LevelSyncRepositoryImpl>() bind LevelSyncRepository::class
  single<TrainingSessionSyncRepositoryImpl>() bind TrainingSessionSyncRepository::class
  single<KtorBackendStatusMonitor>() bind BackendStatusMonitor::class
  single<CourseDefinitionMapper>()
  single<CourseRepositoryImpl>() bind CourseRepository::class
  single<MidiContentResolverImpl>() bind MidiContentResolver::class
}
