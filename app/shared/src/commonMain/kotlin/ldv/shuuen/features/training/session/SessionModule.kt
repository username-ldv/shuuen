package ldv.shuuen.features.training.session

import ldv.shuuen.core.database.AppDatabase
import ldv.shuuen.core.sync.Synchronizer
import ldv.shuuen.features.training.session.storage.TrainingSessionDao
import ldv.shuuen.features.training.session.storage.TrainingSessionRepositoryImpl
import ldv.shuuen.features.training.session.sync.TrainingSessionSyncApi
import ldv.shuuen.features.training.session.sync.TrainingSessionSynchronizer
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** Finished training sessions: their history, statistics and sync. */
val sessionModule = module {
  single<TrainingSessionDao> { get<AppDatabase>().trainingSessionDao() }
  single<TrainingSessionRepositoryImpl>() bind TrainingSessionRepository::class

  single<TrainingSessionSyncApi>()
  single<TrainingSessionSynchronizer>() bind Synchronizer::class
}
