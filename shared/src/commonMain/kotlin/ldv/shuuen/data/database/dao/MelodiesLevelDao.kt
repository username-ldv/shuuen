package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import ldv.shuuen.data.database.entity.MelodiesLevelDbEntity

@Dao
interface MelodiesLevelDao {
  @Query("select * from levels_melodies")
  suspend fun getAll(): List<MelodiesLevelDbEntity>

  @Query("select * from levels_melodies where id = :id")
  suspend fun getById(id: String): MelodiesLevelDbEntity?

  @Upsert
  suspend fun upsertLevel(level: MelodiesLevelDbEntity)

  @Query("delete from levels_melodies where id = :id")
  suspend fun deleteById(id: String)
}
