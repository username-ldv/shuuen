package ldv.shuuen.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseLevelPage
import ldv.shuuen.features.training.course.domain.CourseLevelQuery
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CoursePage
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository

class TrainingLevelResolverImplTest {
  @Test
  fun routesLocalAndRemoteReferencesWithoutChangingLocalIds() = runTest {
    val local = melody("local-id")
    val remoteReference = LevelReference.Remote(5, TrainingFlow.Melodies, "remote-id")
    val remote = melody(remoteReference.encoded)
    val localRepository = FakeMelodiesLocalRepository(local)
    val resolver =
      TrainingLevelResolverImpl(
        singlesRepository = EmptySinglesRepository,
        melodiesRepository = localRepository,
        chordsRepository = EmptyChordsRepository,
        courseRepository = ResolverCourseRepository(remoteReference, remote),
      )

    assertEquals("local-id", resolver.resolveMelodies("local-id").id)
    assertEquals("local-id", localRepository.requestedId)
    assertEquals(remoteReference.encoded, resolver.resolveMelodies(remoteReference.encoded).id)
    assertEquals("local-id", localRepository.requestedId)
  }
}

private fun melody(id: String) =
  MelodiesLevel(
    id = id,
    name = id,
    config =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.AbsoluteScaleConfig(
            Pitch.C,
            ScaleType.Major,
            listOf(ScaleConfig.ScaleItemState.ScalePitchState(Pitch.C, true)),
          ),
        questionsNumber = 1,
        notesPerSequence = 1,
        tempo = 60,
        range = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 4)),
      ),
    context = null,
    source = LevelSource.User,
  )

private class FakeMelodiesLocalRepository(private val level: MelodiesLevel) :
  MelodiesLocalLevelRepository {
  var requestedId: String? = null
  override fun getLevels(): Flow<ResponseState<List<MelodiesLevel>>> =
    flowOf(ResponseState.Success(listOf(level)))
  override fun getLevelById(id: String): Flow<ResponseState<MelodiesLevel>> {
    requestedId = id
    return flowOf(ResponseState.Success(level))
  }
  override suspend fun upsertLevel(level: MelodiesLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private object EmptySinglesRepository : SinglesLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<SinglesLevel>>> = flowOf(ResponseState.Success(emptyList()))
  override fun getLevelById(id: String): Flow<ResponseState<SinglesLevel>> = error("not used")
  override suspend fun upsertLevel(level: SinglesLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private object EmptyChordsRepository : ChordsLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<ChordsLevel>>> = flowOf(ResponseState.Success(emptyList()))
  override fun getLevelById(id: String): Flow<ResponseState<ChordsLevel>> = error("not used")
  override suspend fun upsertLevel(level: ChordsLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private class ResolverCourseRepository(
  private val reference: LevelReference.Remote,
  private val level: MelodiesLevel,
) : CourseRepository {
  override suspend fun listCourses(limit: Int, offset: Int): CoursePage = error("not used")
  override suspend fun getCourse(courseId: Long): CourseSummary = error("not used")
  override suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode = error("not used")
  override suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): CourseLevelPage = error("not used")
  override suspend fun getLevel(reference: LevelReference.Remote): CourseLevelItem =
    CourseLevelItem(
      reference = this.reference,
      playable = PlayableTrainingLevel.Melodies(level),
      progressionGroupId = "group",
      sortOrder = 0,
      sections = emptyList(),
      sourceCourseId = this.reference.courseId,
      mode = this.reference.mode,
    )
  override suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery = error("not used")
}
