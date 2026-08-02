package ldv.shuuen.features.main

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.sync.DataSyncResult
import ldv.shuuen.core.sync.DataSyncStatus
import ldv.shuuen.core.sync.LevelSyncRepository
import ldv.shuuen.core.sync.LevelSyncResult
import ldv.shuuen.core.sync.TrainingSessionSyncRepository
import ldv.shuuen.core.sync.TrainingSessionSyncResult
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseLevelNavigation
import ldv.shuuen.features.training.course.domain.CourseLevelPage
import ldv.shuuen.features.training.course.domain.CourseLevelQuery
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CourseModeSummary
import ldv.shuuen.features.training.course.domain.CoursePage
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PageMeta
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.ProgressionGroup
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel

@OptIn(ExperimentalCoroutinesApi::class)
class MainMenuViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun localLevelCanBeContinuedWithoutCoursePercentage() = runTest(dispatcher) {
    val sessions = FakeTrainingSessionRepository(localSession())
    val courses = FakeCourseRepository()
    val viewModel =
      MainMenuViewModel(
        sessions,
        courses,
        FakeLevelSyncRepository(),
        FakeTrainingSessionSyncRepository(),
      )

    advanceUntilIdle()

    val card = assertNotNull(viewModel.state.value.continueCard)
    assertEquals("local-level", card.levelReference)
    assertFalse(card.isCourseLevel)
    assertNull(card.course)
    assertNull(card.nextLevelReference)
    assertEquals(0, courses.requestCount)
  }

  @Test
  fun latestCourseGroupDrivesContinueNextAndWholeCourseProgress() = runTest(dispatcher) {
    val courses = FakeCourseRepository()
    val latestReference = courses.reference("a")
    val sessions =
      FakeTrainingSessionRepository(
        session(levelId = latestReference, levelName = "Level A"),
        completed = setOf(latestReference, courses.reference("c"), courses.reference("d")),
      )
    val viewModel =
      MainMenuViewModel(
        sessions,
        courses,
        FakeLevelSyncRepository(),
        FakeTrainingSessionSyncRepository(),
      )

    advanceUntilIdle()

    val initial = assertNotNull(viewModel.state.value.continueCard)
    val progress = assertNotNull(initial.course)
    assertEquals("Tonic course", progress.name)
    assertEquals("Foundations", progress.currentGroup?.name)
    assertEquals(50, progress.currentGroup?.progress?.percentage)
    assertEquals(listOf("Foundations", "Advanced"), progress.groups.map { it.name })
    assertEquals(75, progress.total.percentage)
    assertEquals(courses.reference("b"), initial.nextLevelReference)

    sessions.completed.value = courses.allReferences
    advanceUntilIdle()

    val completed = assertNotNull(viewModel.state.value.continueCard?.course)
    assertEquals(100, completed.total.percentage)
    assertTrue(completed.groups.all { it.progress.percentage == 100 })
  }

  @Test
  fun endingLatestLevelEarlyDoesNotIncreaseCourseCompletion() = runTest(dispatcher) {
    val courses = FakeCourseRepository()
    val latestReference = courses.reference("a")
    val sessions =
      FakeTrainingSessionRepository(
        session(levelId = latestReference, levelName = "Level A", finishedEarly = true),
        completed = setOf(courses.reference("c"), courses.reference("d")),
      )
    val viewModel =
      MainMenuViewModel(
        sessions,
        courses,
        FakeLevelSyncRepository(),
        FakeTrainingSessionSyncRepository(),
      )

    advanceUntilIdle()

    val card = assertNotNull(viewModel.state.value.continueCard)
    val progress = assertNotNull(card.course)
    assertEquals(latestReference, card.levelReference)
    assertEquals(courses.reference("b"), card.nextLevelReference)
    assertEquals(0, progress.currentGroup?.progress?.percentage)
    assertEquals(50, progress.total.percentage)
  }

  @Test
  fun manualDataSyncPublishesBothResultsWithoutLosingContinueState() = runTest(dispatcher) {
    val sessions = FakeTrainingSessionRepository(localSession())
    val levelResult = LevelSyncResult(pushed = 2, received = 3, conflicts = 1)
    val sessionResult = TrainingSessionSyncResult(pushed = 4, received = 5, conflicts = 2)
    val levelSync = FakeLevelSyncRepository(levelResult)
    val sessionSync = FakeTrainingSessionSyncRepository(sessionResult)
    val viewModel = MainMenuViewModel(sessions, FakeCourseRepository(), levelSync, sessionSync)
    advanceUntilIdle()

    viewModel.syncData()
    advanceUntilIdle()

    assertEquals(1, levelSync.calls)
    assertEquals(1, sessionSync.calls)
    assertEquals(
      DataSyncStatus.Complete(DataSyncResult(levelResult, sessionResult)),
      viewModel.state.value.dataSyncStatus,
    )
    assertNotNull(viewModel.state.value.continueCard)
  }
}

private class FakeLevelSyncRepository(
  private val result: LevelSyncResult = LevelSyncResult(pushed = 0, received = 0, conflicts = 0),
) : LevelSyncRepository {
  var calls = 0

  override suspend fun sync(): LevelSyncResult {
    calls++
    return result
  }
}

private class FakeTrainingSessionSyncRepository(
  private val result: TrainingSessionSyncResult =
    TrainingSessionSyncResult(pushed = 0, received = 0, conflicts = 0),
) : TrainingSessionSyncRepository {
  var calls = 0

  override suspend fun sync(): TrainingSessionSyncResult {
    calls++
    return result
  }
}

private class FakeTrainingSessionRepository(
  latest: TrainingSession?,
  completed: Set<String> = emptySet(),
) : TrainingSessionRepository {
  private val latest = MutableStateFlow(latest)
  private val attempted = MutableStateFlow(completed + listOfNotNull(latest?.levelId))
  val completed = MutableStateFlow(completed)

  override suspend fun saveSession(session: TrainingSession) {
    latest.value = session
    attempted.value += session.levelId
    if (!session.finishedEarly) completed.value += session.levelId
  }

  override fun getSessionById(id: String): Flow<ResponseState<TrainingSession>> =
    flowOf(ResponseState.Error(IllegalStateException("not implemented")))

  override fun observeLatestSession(): Flow<TrainingSession?> = latest

  override fun observeLevelAccuracyStats(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<LevelAccuracyStats> = flowOf(LevelAccuracyStats(windowSize = limit))

  override fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<Set<String>> = attempted

  override fun observeCompletedLevelIds(flow: TrainingFlow): Flow<Set<String>> = completed

  override suspend fun deleteLastLevelSession(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllLevelSessions(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllCourseSessions(courseId: Long) = Unit
}

private class FakeCourseRepository : CourseRepository {
  val allReferences = listOf("a", "b", "c", "d").map(::reference).toSet()
  var requestCount = 0

  private val groups =
    listOf(
      ProgressionGroup("foundations", "Foundations", "", 0, 2, 0),
      ProgressionGroup("advanced", "Advanced", "", 1, 2, 0),
    )
  private val course =
    CourseSummary(
      id = CourseId,
      slug = "tonic-course",
      name = "Tonic course",
      description = "",
      author = "",
      sortOrder = 0,
      modes = listOf(CourseModeSummary(TrainingFlow.Melodies, "Melodies", "", 0, 2, 4)),
    )

  fun reference(id: String): String =
    LevelReference.Remote(CourseId, TrainingFlow.Melodies, id).encoded

  override suspend fun listCourses(limit: Int, offset: Int): CoursePage =
    CoursePage(listOf(course), PageMeta(limit, offset, 1))

  override suspend fun getCourse(courseId: Long): CourseSummary {
    requestCount += 1
    return course
  }

  override suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode {
    requestCount += 1
    return CourseMode(mode, "Melodies", "", 0, 2, 4, groups)
  }

  override suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): CourseLevelPage = error("not implemented")

  override suspend fun getLevel(reference: LevelReference.Remote): CourseLevelItem {
    requestCount += 1
    return item(reference.levelId)
  }

  override suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery {
    requestCount += 1
    return CourseLevelQuery(levelIds.map(::item))
  }

  private fun item(id: String): CourseLevelItem {
    val reference = LevelReference.Remote(CourseId, TrainingFlow.Melodies, id)
    val groupId = if (id == "a" || id == "b") "foundations" else "advanced"
    val next = when (id) {
      "a" -> "b"
      "c" -> "d"
      else -> null
    }
    return CourseLevelItem(
      reference = reference,
      playable = PlayableTrainingLevel.Melodies(testLevel(reference.encoded, "Level ${id.uppercase()}")),
      progressionGroupId = groupId,
      sortOrder = id.first().code,
      sections = emptyList(),
      sourceCourseId = CourseId,
      mode = TrainingFlow.Melodies,
      navigation = CourseLevelNavigation(null, next, 1, 2),
    )
  }

  private companion object {
    const val CourseId = 7L
  }
}

private fun localSession() =
  session(levelId = "local-level", levelName = "My local level")

private fun session(
  levelId: String,
  levelName: String,
  finishedEarly: Boolean = false,
) =
  TrainingSession(
    id = "session",
    flow = TrainingFlow.Melodies,
    levelId = levelId,
    levelName = levelName,
    completedAtEpochMillis = 1,
    finishedEarly = finishedEarly,
    questionsAnswered = 1,
    notesTotal = 1,
    correctNotes = 1,
    missedNotes = 0,
    replays = 0,
    durationMillis = 100,
    avgAnswerMillis = null,
    avgDeltaMillis = 0,
    bestStreak = 1,
    keysPracticed = 1,
    questionResults = emptyList(),
  )

private fun testLevel(id: String, name: String) =
  MelodiesLevel(
    id = id,
    name = name,
    config =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.AbsoluteScaleConfig(
            Pitch.C,
            ScaleType.Major,
            Pitch.entries.map {
              ScaleConfig.ScaleItemState.ScalePitchState(it, it == Pitch.C)
            },
          ),
        questionsNumber = 1,
        notesPerSequence = 1,
        tempo = 60,
        range = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 4)),
      ),
    context = null,
    source = LevelSource.BuiltIn,
  )
