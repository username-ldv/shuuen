package ldv.shuuen.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import ldv.shuuen.data.database.entity.ContextDbEntity
import ldv.shuuen.data.database.entity.SinglesLevelDbEntity

@Dao
interface ContextDao {
  @Query("select * from context where id = :id")
  suspend fun getById(id: String): ContextDbEntity?

  @Query("select * from context")
  suspend fun getAll(): List<ContextDbEntity>

  @Upsert
  suspend fun upsertContext(context: ContextDbEntity)
}