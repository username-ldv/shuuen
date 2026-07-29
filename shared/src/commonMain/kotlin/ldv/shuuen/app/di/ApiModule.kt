package ldv.shuuen.app.di

import io.ktor.client.HttpClient
import ldv.shuuen.data.remote.ApiConfig
import ldv.shuuen.data.remote.ApiJson
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

val apiModule = module {
  single { ApiConfig(defaultApiBaseUrl()) }
  single<HttpClient> { createPlatformApiHttpClient(ApiJson) }
  single { CourseApi(get(), get()) }
  single { CourseDefinitionMapper(ApiJson) }
  single { CourseRepositoryImpl(get(), get()) } bind CourseRepository::class
  single { MidiContentResolverImpl(get()) } bind MidiContentResolver::class
}
