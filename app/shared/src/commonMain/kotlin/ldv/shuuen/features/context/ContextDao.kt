package ldv.shuuen.features.context

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface ContextDao {
  @Query("select * from context where id = :id") suspend fun getById(id: String): ContextDbEntity?

  @Query("select * from context") suspend fun getAll(): List<ContextDbEntity>

  @Upsert suspend fun upsertContext(context: ContextDbEntity)
}
