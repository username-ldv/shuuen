package ldv.shuuen.features.training.level

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SinglesLevelDao {
  @Transaction
  @Query("select * from levels_singles")
  fun observeAll(): Flow<List<SinglesLevelWithContext>>

  @Transaction
  @Query("select * from levels_singles where id = :id")
  suspend fun getById(id: String): SinglesLevelWithContext?

  /** The bare rows, for sync. */
  @Query("select * from levels_singles") suspend fun getAll(): List<SinglesLevelDbEntity>

  @Upsert suspend fun upsertLevel(level: SinglesLevelDbEntity)

  @Query("delete from levels_singles where id = :id") suspend fun deleteById(id: String)
}
