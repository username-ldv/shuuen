package ldv.shuuen.features.training.melodies.level_select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.settings.CourseOverrides
import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.MidiLevelOptions
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import ldv.shuuen.core.ui.ScreenState
import ldv.shuuen.core.ui.asScreenState
import ldv.shuuen.features.training.common.setup.TuneInconsistencyRange
import ldv.shuuen.features.training.course.CourseRepository
import ldv.shuuen.features.training.course.presentation.CourseLevelBrowser
import ldv.shuuen.features.training.level.MelodiesLevel
import ldv.shuuen.features.training.level.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.level.PlayableTrainingLevel
import ldv.shuuen.features.training.level.TrainingFlow
import ldv.shuuen.features.training.session.LevelAccuracyStats
import ldv.shuuen.features.training.session.TrainingSessionRepository

class MelodiesLevelSelectScreenViewModel(
  private val levelRepository: MelodiesLocalLevelRepository,
  private val settingsRepository: SettingsRepository,
  private val trainingSessionRepository: TrainingSessionRepository,
  courseRepository: CourseRepository,
) : ViewModel() {
  /** Updates by itself after an edit, a delete or a sync. */
  val levels: StateFlow<ScreenState<List<MelodiesLevel>>> =
    levelRepository
      .observeLevels()
      .asScreenState()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

  private val courseBrowser =
    CourseLevelBrowser(
      mode = TrainingFlow.Melodies,
      repository = courseRepository,
      scope = viewModelScope,
      extract = { (it as? PlayableTrainingLevel.Melodies)?.level },
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

  /** Shared MIDI playback options; the options sheet edits them and every MIDI level reads them. */
  val midiLevelOptions: StateFlow<MidiLevelOptions> =
    settingsRepository.settings
      .map { it.midiLevelOptions }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MidiLevelOptions())

  fun saveMidiLevelOptions(options: MidiLevelOptions) {
    viewModelScope.launch { settingsRepository.setMidiLevelOptions(options) }
  }

  fun setMidiContext(context: DegreeContext?) {
    viewModelScope.launch {
      val current = settingsRepository.settings.first().midiLevelOptions
      settingsRepository.setMidiLevelOptions(current.copy(context = context))
    }
  }

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
      .observeAttemptedLevelIds(TrainingFlow.Melodies)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

  @OptIn(ExperimentalCoroutinesApi::class)
  fun levelStats(levelId: String): Flow<LevelAccuracyStats> = statsWindow.flatMapLatest { window ->
    trainingSessionRepository.observeLevelAccuracyStats(TrainingFlow.Melodies, levelId, window)
  }

  fun deleteLevel(levelId: String) {
    viewModelScope.launch {
      levelRepository.deleteLevel(levelId)
    }
  }

  fun deleteLastPlayStatistics(levelId: String) {
    viewModelScope.launch {
      trainingSessionRepository.deleteLastLevelSession(TrainingFlow.Melodies, levelId)
    }
  }

  fun deleteAllLevelStatistics(levelId: String) {
    viewModelScope.launch {
      trainingSessionRepository.deleteAllLevelSessions(TrainingFlow.Melodies, levelId)
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
