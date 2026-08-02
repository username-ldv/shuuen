package ldv.shuuen.data.remote.sync

import kotlinx.serialization.Serializable

@Serializable
internal data class TrainingQuestionResultDto(
  val questionNumber: Int,
  val noteCount: Int,
  val missedCount: Int,
)

@Serializable
internal data class TrainingSessionSyncMutationDto(
  val id: String,
  val baseRevision: Long,
  val deleted: Boolean = false,
  val flow: String = "",
  val levelId: String = "",
  val levelName: String = "",
  val completedAtEpochMillis: Long = 0,
  val finishedEarly: Boolean = false,
  val questionsAnswered: Int = 0,
  val notesTotal: Int = 0,
  val correctNotes: Int = 0,
  val missedNotes: Int = 0,
  val replays: Int = 0,
  val durationMillis: Long = 0,
  val avgAnswerMillis: Long? = null,
  val avgDeltaMillis: Long? = null,
  val bestStreak: Int = 0,
  val keysPracticed: Int = 0,
  val questionResults: List<TrainingQuestionResultDto> = emptyList(),
)

@Serializable
internal data class TrainingSessionSyncChangeDto(
  val id: String,
  val revision: Long,
  val deleted: Boolean,
  val flow: String = "",
  val levelId: String = "",
  val levelName: String = "",
  val completedAtEpochMillis: Long = 0,
  val finishedEarly: Boolean = false,
  val questionsAnswered: Int = 0,
  val notesTotal: Int = 0,
  val correctNotes: Int = 0,
  val missedNotes: Int = 0,
  val replays: Int = 0,
  val durationMillis: Long = 0,
  val avgAnswerMillis: Long? = null,
  val avgDeltaMillis: Long? = null,
  val bestStreak: Int = 0,
  val keysPracticed: Int = 0,
  val questionResults: List<TrainingQuestionResultDto> = emptyList(),
)

@Serializable
internal data class TrainingSessionSyncRequestDto(
  val sinceRevision: Long,
  val changes: List<TrainingSessionSyncMutationDto>,
)

@Serializable
internal data class TrainingSessionSyncResponseDto(
  val revision: Long,
  val applied: Int,
  val conflicts: Int,
  val changes: List<TrainingSessionSyncChangeDto>,
)

@Serializable
internal data class StoredTrainingSessionSyncRegistry(
  val accounts: Map<String, StoredTrainingSessionSyncAccount> = emptyMap(),
)

@Serializable
internal data class StoredTrainingSessionSyncAccount(
  val revision: Long = 0,
  val sessions: Map<String, StoredTrainingSessionSyncEntry> = emptyMap(),
)

@Serializable
internal data class StoredTrainingSessionSyncEntry(
  val revision: Long,
  /** Null is an acknowledged tombstone; non-null is the last synced content fingerprint. */
  val fingerprint: String? = null,
)
