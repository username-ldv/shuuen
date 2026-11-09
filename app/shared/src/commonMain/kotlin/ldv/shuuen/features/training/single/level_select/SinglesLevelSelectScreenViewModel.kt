package ldv.shuuen.features.training.single.level_select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ldv.shuuen.core.settings.CourseOverrides
import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.core.ui.ScreenState
import ldv.shuuen.core.ui.asScreenState
import ldv.shuuen.features.training.common.setup.TuneInconsistencyRange
import ldv.shuuen.features.training.course.CourseRepository
import ldv.shuuen.features.training.course.presentation.CourseLevelBrowser
import ldv.shuuen.features.training.level.PlayableTrainingLevel
import ldv.shuuen.features.training.level.SinglesLevel
import ldv.shuuen.features.training.level.SinglesLocalLevelRepository
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.session.LevelAccuracyStats
import ldv.shuuen.features.training.session.TrainingSessionRepository

class SinglesLevelSelectScreenViewModel(
  private val levelRepository: SinglesLocalLevelRepository,
  private val settingsRepository: SettingsRepository,
  private val trainingSessionRepository: TrainingSessionRepository,
  courseRepository: CourseRepository,
) : ViewModel() {
  /** Updates by itself after an edit, a delete or a sync. */
  val levels: StateFlow<ScreenState<List<SinglesLevel>>> =
    levelRepository
      .observeLevels()
      .asScreenState()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

  private val courseBrowser =
    CourseLevelBrowser(
      mode = TrainingFlow.Singles,
      repository = courseRepository,
      scope = viewModelScope,
      extract = { (it as? PlayableTrainingLevel.Singles)?.level },
    )
  val courseState = courseBrowser.state

  init {
    courseBrowser.refreshCourses()
  }

  private val statsWindow =
    settingsRepository.settings
      .map { coerceLevelStatsWindow(it.levelStatsWindow) }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultLevelStatsWindow)

  /**
   * Local per-course level overrides, applied to course level cards and edited in the course sheet.
   */
  val courseOverrides: StateFlow<Map<Long, CourseOverrides>> =
    settingsRepository.settings
      .map { it.courseOverrides }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

  fun setCourseTuneInconsistency(courseId: Long, cents: Int?) {
    viewModelScope.launch {
      settingsRepository.setCourseTuneInconsistency(
        courseId,
        cents?.coerceIn(TuneInconsistencyRange),
      )
    }
  }

  val attemptedLevelIds =
    trainingSessionRepository
      .observeAttemptedLevelIds(TrainingFlow.Singles)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

  @OptIn(ExperimentalCoroutinesApi::class)
  fun levelStats(levelId: String): Flow<LevelAccuracyStats> = statsWindow.flatMapLatest { window ->
    trainingSessionRepository.observeLevelAccuracyStats(TrainingFlow.Singles, levelId, window)
  }

  fun deleteLevel(levelId: String) {
    viewModelScope.launch {
      levelRepository.deleteLevel(levelId)
    }
  }

  fun deleteLastPlayStatistics(levelId: String) {
    viewModelScope.launch {
      trainingSessionRepository.deleteLastLevelSession(TrainingFlow.Singles, levelId)
    }
  }

  fun deleteAllLevelStatistics(levelId: String) {
    viewModelScope.launch {
      trainingSessionRepository.deleteAllLevelSessions(TrainingFlow.Singles, levelId)
    }
  }

  fun deleteAllCourseStatistics(courseId: Long) {
    viewModelScope.launch { trainingSessionRepository.deleteAllCourseSessions(courseId) }
  }

  fun refreshCourses() = courseBrowser.refreshCourses()

  fun selectMyLevels() = courseBrowser.selectMyLevels()

  fun selectCourse(courseId: Long) = courseBrowser.selectCourse(courseId)

  fun selectGroup(groupId: String) = courseBrowser.selectGroup(groupId)

  fun loadNextPage() = courseBrowser.loadNextPage()

  suspend fun firstUnattemptedCourseLevel(attemptedLevelIds: Set<String>): String? =
    courseBrowser.firstUnattemptedReference(attemptedLevelIds)

  fun retryCourseLevels() = courseBrowser.retryLevels()
}
