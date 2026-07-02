package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity

@Dao
interface TrainingSessionDao {
  @Query("select * from training_sessions where id = :id")
  suspend fun getById(id: String): TrainingSessionDbEntity?

  @Query("select * from training_sessions where levelId = :levelId order by completedAtEpochMillis desc")
  suspend fun getByLevelId(levelId: String): List<TrainingSessionDbEntity>

  @Upsert
  suspend fun upsertSession(session: TrainingSessionDbEntity)
}
