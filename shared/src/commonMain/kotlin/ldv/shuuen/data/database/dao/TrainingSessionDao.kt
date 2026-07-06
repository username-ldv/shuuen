package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity
import ldv.shuuen.features.training.common.TrainingFlow

data class TrainingSessionScoreProjection(
  val correctNotes: Int,
  val notesTotal: Int,
)

@Dao
interface TrainingSessionDao {
  @Query("select * from training_sessions where id = :id")
  suspend fun getById(id: String): TrainingSessionDbEntity?

  @Query("select * from training_sessions where levelId = :levelId order by completedAtEpochMillis desc")
  suspend fun getByLevelId(levelId: String): List<TrainingSessionDbEntity>

  @Query(
    """
    select correctNotes, notesTotal
    from training_sessions
    where levelId = :levelId
    and flow = :flow
    order by completedAtEpochMillis desc
    limit :limit
    """
  )
  fun observeRecentScoresByLevelId(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<List<TrainingSessionScoreProjection>>

  @Upsert
  suspend fun upsertSession(session: TrainingSessionDbEntity)
}
