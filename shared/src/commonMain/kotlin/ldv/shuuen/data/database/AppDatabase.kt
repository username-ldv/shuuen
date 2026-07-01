package ldv.shuuen.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import ldv.shuuen.data.database.converters.ContextTypeConverter
import ldv.shuuen.data.database.converters.GeneralTypeConverter
import ldv.shuuen.data.database.converters.MelodiesLevelTypeConverter
import ldv.shuuen.data.database.converters.SinglesLevelTypeConverter
import ldv.shuuen.data.database.dao.ContextDao
import ldv.shuuen.data.database.dao.MelodiesLevelDao
import ldv.shuuen.data.database.dao.SinglesLevelDao
import ldv.shuuen.data.database.entity.ContextDbEntity
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity

@Database(
  entities = [SinglesLevelDbEntity::class, MelodiesLevelDbEntity::class, ContextDbEntity::class],
  version = 5,
)
@TypeConverters(
  GeneralTypeConverter::class,
  SinglesLevelTypeConverter::class,
  MelodiesLevelTypeConverter::class,
  ContextTypeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun singlesLevelDao(): SinglesLevelDao
  abstract fun melodiesLevelDao(): MelodiesLevelDao
  abstract fun contextDao(): ContextDao
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase
}

fun createDatabase(
  builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
  return builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
}