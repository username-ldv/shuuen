package ldv.shuuen.core.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import ldv.shuuen.features.context.ContextDao
import ldv.shuuen.features.context.ContextDbEntity
import ldv.shuuen.features.training.level.ChordsLevelDao
import ldv.shuuen.features.training.level.ChordsLevelDbEntity
import ldv.shuuen.features.training.level.MelodiesLevelDao
import ldv.shuuen.features.training.level.MelodiesLevelDbEntity
import ldv.shuuen.features.training.level.SinglesLevelDao
import ldv.shuuen.features.training.level.SinglesLevelDbEntity
import ldv.shuuen.features.training.session.TrainingSessionDao
import ldv.shuuen.features.training.session.TrainingSessionDbEntity

/**
 * The one central Room database. It lists every feature's entities; each feature provides its own
 * DAO binding and its own column type converters (on the entity). See [Migrations].
 */
@Database(
  entities =
    [
      SinglesLevelDbEntity::class,
      MelodiesLevelDbEntity::class,
      ChordsLevelDbEntity::class,
      ContextDbEntity::class,
      TrainingSessionDbEntity::class,
    ],
  version = 8,
)
@ColumnTypeConverters(GeneralTypeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun singlesLevelDao(): SinglesLevelDao

  abstract fun melodiesLevelDao(): MelodiesLevelDao

  abstract fun chordsLevelDao(): ChordsLevelDao

  abstract fun contextDao(): ContextDao

  abstract fun trainingSessionDao(): TrainingSessionDao
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase
}

fun createDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
  return builder
    .addMigrations(*Migrations)
    .fallbackToDestructiveMigrationFrom(dropAllTables = true, *UnmigratedVersions)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
}
