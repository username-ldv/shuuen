package ldv.shuuen.features.training.level.storage

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChordsLevelDao {
  @Transaction
  @Query("select * from levels_chords")
  fun observeAll(): Flow<List<ChordsLevelWithContext>>

  @Transaction
  @Query("select * from levels_chords where id = :id")
  suspend fun getById(id: String): ChordsLevelWithContext?

  /** The bare rows, for sync. */
  @Query("select * from levels_chords") suspend fun getAll(): List<ChordsLevelDbEntity>

  @Upsert suspend fun upsertLevel(level: ChordsLevelDbEntity)

  @Query("delete from levels_chords where id = :id") suspend fun deleteById(id: String)
}
