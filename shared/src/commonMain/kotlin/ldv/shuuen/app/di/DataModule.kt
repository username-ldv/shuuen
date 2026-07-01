package ldv.shuuen.app.di

import ldv.shuuen.data.database.AppDatabase
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.repository.local.ContextLocalRepositoryImpl
import ldv.shuuen.data.repository.local.MelodiesLocalLevelRepositoryImpl
import ldv.shuuen.data.repository.local.SinglesLocalLevelRepositoryImpl
import ldv.shuuen.data.settings.KStoreSettingsRepository
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.features.context.domain.ContextLocalRepository
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val dataModule = module {
  single<KStoreSettingsRepository>() bind SettingsRepository::class

  single<SinglesLevelDao> { get<AppDatabase>().singlesLevelDao() }
  single<MelodiesLevelDao> { get<AppDatabase>().melodiesLevelDao() }
  single<ContextDao> { get<AppDatabase>().contextDao() }

  single<ContextLocalRepositoryImpl>() bind ContextLocalRepository::class
  single<SinglesLocalLevelRepositoryImpl>() bind SinglesLocalLevelRepository::class
  single<MelodiesLocalLevelRepositoryImpl>() bind MelodiesLocalLevelRepository::class
}