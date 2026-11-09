package ldv.shuuen.features.training.level

import ldv.shuuen.core.database.AppDatabase
import ldv.shuuen.core.sync.Synchronizer
import ldv.shuuen.features.training.level.definition.LevelDefinitionCodec
import ldv.shuuen.features.training.level.storage.ChordsLevelDao
import ldv.shuuen.features.training.level.storage.ChordsLocalLevelRepositoryImpl
import ldv.shuuen.features.training.level.storage.MelodiesLevelDao
import ldv.shuuen.features.training.level.storage.MelodiesLocalLevelRepositoryImpl
import ldv.shuuen.features.training.level.storage.SinglesLevelDao
import ldv.shuuen.features.training.level.storage.SinglesLocalLevelRepositoryImpl
import ldv.shuuen.features.training.level.sync.LevelSyncApi
import ldv.shuuen.features.training.level.sync.LevelSynchronizer
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** Storage and sync of the user's own levels, shared by every training flow. */
val levelModule = module {
  single<SinglesLevelDao> { get<AppDatabase>().singlesLevelDao() }
  single<MelodiesLevelDao> { get<AppDatabase>().melodiesLevelDao() }
  single<ChordsLevelDao> { get<AppDatabase>().chordsLevelDao() }

  single<SinglesLocalLevelRepositoryImpl>() bind SinglesLocalLevelRepository::class
  single<MelodiesLocalLevelRepositoryImpl>() bind MelodiesLocalLevelRepository::class
  single<ChordsLocalLevelRepositoryImpl>() bind ChordsLocalLevelRepository::class

  single<LevelDefinitionCodec>()
  single<LevelSyncApi>()
  single<LevelSynchronizer>() bind Synchronizer::class
}
