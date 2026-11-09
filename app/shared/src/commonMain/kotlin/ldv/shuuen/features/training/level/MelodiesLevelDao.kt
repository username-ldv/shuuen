package ldv.shuuen.features.training.level

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MelodiesLevelDao {
  @Transaction
  @Query("select * from levels_melodies")
  fun observeAll(): Flow<List<MelodiesLevelWithContext>>

  @Transaction
  @Query("select * from levels_melodies where id = :id")
  suspend fun getById(id: String): MelodiesLevelWithContext?

  /** The bare rows, for sync. */
  @Query("select * from levels_melodies") suspend fun getAll(): List<MelodiesLevelDbEntity>

  @Upsert suspend fun upsertLevel(level: MelodiesLevelDbEntity)

  @Query("delete from levels_melodies where id = :id") suspend fun deleteById(id: String)
}
