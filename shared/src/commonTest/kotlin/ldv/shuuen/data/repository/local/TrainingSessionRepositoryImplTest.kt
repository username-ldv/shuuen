package ldv.shuuen.data.repository.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ldv.shuuen.data.database.dao.TrainingSessionDao
import ldv.shuuen.data.database.dao.TrainingSessionScoreProjection
import ldv.shuuen.data.database.entity.TrainingSessionDbEntity
import ldv.shuuen.features.training.common.TrainingFlow

class TrainingSessionRepositoryImplTest {
  @Test
  fun exposesTheLatestPersistedSession() = runTest {
    val dao = RecordingTrainingSessionDao()
    dao.latestSession = testSessionEntity()
    val repository = TrainingSessionRepositoryImpl(dao)

    val latest = repository.observeLatestSession().first()

    assertEquals("latest-session", latest?.id)
    assertEquals("Latest level", latest?.levelName)
    assertEquals(42, latest?.completedAtEpochMillis)
  }

  @Test
  fun levelStatisticsDeletionKeepsFlowAndLevelIdentity() = runTest {
    val dao = RecordingTrainingSessionDao()
    val repository = TrainingSessionRepositoryImpl(dao)

    repository.deleteLastLevelSession(TrainingFlow.Chords, "same-id")
    repository.deleteAllLevelSessions(TrainingFlow.Melodies, "same-id")

    assertEquals(TrainingFlow.Chords to "same-id", dao.lastLevelDeletion)
    assertEquals(TrainingFlow.Melodies to "same-id", dao.allLevelDeletion)
  }

  @Test
  fun courseStatisticsDeletionUsesAnExactCourseReferencePrefix() = runTest {
    val dao = RecordingTrainingSessionDao()
    val repository = TrainingSessionRepositoryImpl(dao)

    repository.deleteAllCourseSessions(12)

    assertEquals("course:12:", dao.courseReferencePrefix)
  }

  @Test
  fun courseStatisticsDeletionRejectsInvalidCourseIds() = runTest {
    val dao = RecordingTrainingSessionDao()
    val repository = TrainingSessionRepositoryImpl(dao)

    assertFailsWith<IllegalArgumentException> { repository.deleteAllCourseSessions(0) }
    assertEquals(null, dao.courseReferencePrefix)
  }
}

private class RecordingTrainingSessionDao : TrainingSessionDao {
  var latestSession: TrainingSessionDbEntity? = null
  var lastLevelDeletion: Pair<TrainingFlow, String>? = null
  var allLevelDeletion: Pair<TrainingFlow, String>? = null
  var courseReferencePrefix: String? = null

  override suspend fun getById(id: String): TrainingSessionDbEntity? = null

  override fun observeLatest(): Flow<TrainingSessionDbEntity?> = flowOf(latestSession)

  override suspend fun getByLevelId(levelId: String): List<TrainingSessionDbEntity> = emptyList()

  override fun observeRecentScoresByLevelId(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<List<TrainingSessionScoreProjection>> = flowOf(emptyList())

  override fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<List<String>> =
    flowOf(emptyList())

  override fun observeCompletedLevelIds(flow: TrainingFlow): Flow<List<String>> =
    flowOf(emptyList())

  override suspend fun deleteLastByLevelId(flow: TrainingFlow, levelId: String) {
    lastLevelDeletion = flow to levelId
  }

  override suspend fun deleteAllByLevelId(flow: TrainingFlow, levelId: String) {
    allLevelDeletion = flow to levelId
  }

  override suspend fun deleteAllByCourseReferencePrefix(courseReferencePrefix: String) {
    this.courseReferencePrefix = courseReferencePrefix
  }

  override suspend fun upsertSession(session: TrainingSessionDbEntity) = Unit
}

private fun testSessionEntity() =
  TrainingSessionDbEntity(
    id = "latest-session",
    flow = TrainingFlow.Singles,
    levelId = "level",
    levelName = "Latest level",
    completedAtEpochMillis = 42,
    finishedEarly = false,
    questionsAnswered = 1,
    notesTotal = 1,
    correctNotes = 1,
    missedNotes = 0,
    replays = 0,
    durationMillis = 100,
    avgAnswerMillis = 50,
    avgDeltaMillis = null,
    bestStreak = 1,
    keysPracticed = 1,
    questionResults = emptyList(),
  )
