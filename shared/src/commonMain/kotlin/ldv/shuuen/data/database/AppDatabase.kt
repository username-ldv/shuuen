package ldv.shuuen.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import ldv.shuuen.data.database.converters.ChordsLevelTypeConverter
import ldv.shuuen.data.database.converters.ContextTypeConverter
import ldv.shuuen.data.database.converters.GeneralTypeConverter
import ldv.shuuen.data.database.converters.MelodiesLevelTypeConverter
import ldv.shuuen.data.database.converters.SinglesLevelTypeConverter
import ldv.shuuen.data.database.converters.TrainingSessionTypeConverter
import ldv.shuuen.data.database.dao.ChordsLevelDao
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.database.dao.TrainingSessionDao
import ldv.shuuen.data.database.entity.ChordsLevelDbEntity
import ldv.shuuen.data.database.entity.ContextDbEntity
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity

@Database(
  entities = [
    SinglesLevelDbEntity::class,
    MelodiesLevelDbEntity::class,
    ChordsLevelDbEntity::class,
    ContextDbEntity::class,
    TrainingSessionDbEntity::class,
  ],
  version = 8,
)
@TypeConverters(
  GeneralTypeConverter::class,
  SinglesLevelTypeConverter::class,
  MelodiesLevelTypeConverter::class,
  ChordsLevelTypeConverter::class,
  ContextTypeConverter::class,
  TrainingSessionTypeConverter::class,
)
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

fun createDatabase(
  builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
  return builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
}