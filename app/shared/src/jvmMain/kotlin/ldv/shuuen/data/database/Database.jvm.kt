package ldv.shuuen.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import io.github.aakira.napier.Napier
import kotlinx.io.files.Path

fun getDatabaseBuilder(filesPath: Path): RoomDatabase.Builder<AppDatabase> {
  val dbFilePath = Path(filesPath, "shuuen.db").toString()
  Napier.v { "Desktop database path $dbFilePath" }
  return Room.databaseBuilder<AppDatabase>(
    name = dbFilePath,
  ).fallbackToDestructiveMigration()
}