package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
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
  @Query("select * from training_sessions")
  suspend fun getAll(): List<TrainingSessionDbEntity>

  @Query("select * from training_sessions where id = :id")
  suspend fun getById(id: String): TrainingSessionDbEntity?

  @Query(
    """
    select *
    from training_sessions
    order by completedAtEpochMillis desc, id desc
    limit 1
    """
  )
  fun observeLatest(): Flow<TrainingSessionDbEntity?>

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

  @Query("select distinct levelId from training_sessions where flow = :flow")
  fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<List<String>>

  @Query(
    "select distinct levelId from training_sessions where flow = :flow and finishedEarly = 0"
  )
  fun observeCompletedLevelIds(flow: TrainingFlow): Flow<List<String>>

  @Query(
    """
    delete from training_sessions
    where id = (
      select id
      from training_sessions
      where flow = :flow and levelId = :levelId
      order by completedAtEpochMillis desc, id desc
      limit 1
    )
    """
  )
  suspend fun deleteLastByLevelId(flow: TrainingFlow, levelId: String)

  @Query("delete from training_sessions where flow = :flow and levelId = :levelId")
  suspend fun deleteAllByLevelId(flow: TrainingFlow, levelId: String)

  @Query("delete from training_sessions where levelId like :courseReferencePrefix || '%'")
  suspend fun deleteAllByCourseReferencePrefix(courseReferencePrefix: String)

  @Query("delete from training_sessions where id = :id")
  suspend fun deleteById(id: String)

  @Query("delete from training_sessions where id in (:ids)")
  suspend fun deleteByIds(ids: List<String>)

  @Upsert
  suspend fun upsertSession(session: TrainingSessionDbEntity)

  @Upsert
  suspend fun upsertSessions(sessions: List<TrainingSessionDbEntity>)

  /** Applies a remote page atomically so observers see one coherent history update. */
  @Transaction
  suspend fun applySyncChanges(
    deletedIds: List<String>,
    sessions: List<TrainingSessionDbEntity>,
  ) {
    if (deletedIds.isNotEmpty()) deleteByIds(deletedIds)
    if (sessions.isNotEmpty()) upsertSessions(sessions)
  }
}
