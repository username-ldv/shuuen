package ldv.shuuen.data.remote.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class LevelSyncRequestDto(
  val sinceRevision: Long,
  val changes: List<LevelSyncMutationDto>,
)

@Serializable
internal data class LevelSyncMutationDto(
  val kind: String,
  val id: String,
  val baseRevision: Long,
  val deleted: Boolean = false,
  val name: String = "",
  val source: String = "",
  val definition: JsonElement? = null,
)

@Serializable
internal data class LevelSyncChangeDto(
  val kind: String,
  val id: String,
  val revision: Long,
  val deleted: Boolean,
  val name: String = "",
  val source: String = "",
  val definition: JsonElement? = null,
)

@Serializable
internal data class LevelSyncResponseDto(
  val revision: Long,
  val applied: Int,
  val conflicts: Int,
  val changes: List<LevelSyncChangeDto>,
)

@Serializable
internal data class LevelSyncEnvelopeDto<T>(val data: T)

@Serializable
internal data class StoredLevelSyncRegistry(
  val accounts: Map<String, StoredLevelSyncAccount> = emptyMap(),
)

@Serializable
internal data class StoredLevelSyncAccount(
  val revision: Long = 0,
  val levels: Map<String, StoredLevelSyncEntry> = emptyMap(),
)

@Serializable
internal data class StoredLevelSyncEntry(
  val revision: Long,
  /** Null is an acknowledged tombstone; non-null is the last synced content fingerprint. */
  val fingerprint: String? = null,
)
