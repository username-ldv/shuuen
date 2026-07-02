package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import ldv.shuuen.data.database.entity.ChordsLevelDbEntity

@Dao
interface ChordsLevelDao {
  @Query("select * from levels_chords")
  suspend fun getAll(): List<ChordsLevelDbEntity>

  @Query("select * from levels_chords where id = :id")
  suspend fun getById(id: String): ChordsLevelDbEntity?

  @Upsert
  suspend fun upsertLevel(level: ChordsLevelDbEntity)
}
