package ldv.shuuen.features.training.course.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseLevelPage
import ldv.shuuen.features.training.course.domain.CourseLevelQuery
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CourseModeSummary
import ldv.shuuen.features.training.course.domain.CoursePage
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSection
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PageMeta
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.ProgressionGroup
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel

@OptIn(ExperimentalCoroutinesApi::class)
class CourseLevelBrowserTest {
  @Test
  fun loadsAnotherPageWithoutDuplicatesAndPreservesSections() = runTest {
    val repository = PagingCourseRepository()
    val browser =
      CourseLevelBrowser(
        mode = TrainingFlow.Melodies,
        repository = repository,
        scope = this,
        extract = { (it as? PlayableTrainingLevel.Melodies)?.level },
      )

    browser.refreshCourses()
    advanceUntilIdle()
    browser.selectCourse(1)
    advanceUntilIdle()

    assertEquals(20, browser.state.value.levels.size)
    assertEquals("group", browser.state.value.selectedGroupId)
    browser.loadNextPage()
    advanceUntilIdle()

    val state = browser.state.value
    assertEquals(25, state.levels.size)
    assertEquals(25, state.levels.map { it.reference }.distinct().size)
    assertEquals(listOf("Etudes", "Chromatic"), state.levels[22].sections.map { it.name })
    assertFalse(state.canLoadMore)
    assertEquals(listOf(0, 20), repository.requestedOffsets)
  }
}

private class PagingCourseRepository : CourseRepository {
  val requestedOffsets = mutableListOf<Int>()
  private val course =
    CourseSummary(
      id = 1,
      slug = "course",
      name = "Course",
      description = "",
      author = "",
      sortOrder = 0,
      modes = listOf(CourseModeSummary(TrainingFlow.Melodies, "Melodies", "", 0, 1, 25)),
    )
  private val group = ProgressionGroup("group", "Group", "", 0, 25, 2)

  override suspend fun listCourses(limit: Int, offset: Int): CoursePage =
    CoursePage(listOf(course), PageMeta(limit, offset, 1))

  override suspend fun getCourse(courseId: Long): CourseSummary = course

  override suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode =
    CourseMode(mode, "Melodies", "", 0, 1, 25, listOf(group))

  override suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): CourseLevelPage {
    requestedOffsets += offset
    val indexes =
      if (offset == 0) 0 until 20
      else listOf(19) + (20 until 25)
    return CourseLevelPage(
      levels = indexes.map { remoteLevel(it) },
      meta = PageMeta(limit, offset, 25),
    )
  }

  override suspend fun getLevel(reference: LevelReference.Remote): CourseLevelItem =
    remoteLevel(reference.levelId.removePrefix("level-").toInt())

  override suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery =
    CourseLevelQuery(levelIds.map { remoteLevel(it.removePrefix("level-").toInt()) })

  private fun remoteLevel(index: Int): CourseLevelItem {
    val reference = LevelReference.Remote(1, TrainingFlow.Melodies, "level-$index")
    val sections =
      if (index == 22) {
        listOf(
          CourseSection(10, "Etudes", "course/group/etudes", 1),
          CourseSection(11, "Chromatic", "course/group/etudes/chromatic", 2),
        )
      } else {
        emptyList()
      }
    val level =
      MelodiesLevel(
        id = reference.encoded,
        name = "Level $index",
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
    return CourseLevelItem(
      reference = reference,
      playable = PlayableTrainingLevel.Melodies(level),
      progressionGroupId = "group",
      sortOrder = index,
      sections = sections,
      sourceCourseId = 1,
      mode = TrainingFlow.Melodies,
    )
  }
}
