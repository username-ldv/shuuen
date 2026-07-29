package ldv.shuuen.features.training.course.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSection
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.course.domain.ProgressionGroup

sealed interface CourseSourceSelection {
  data object MyLevels : CourseSourceSelection
  data class Course(val courseId: Long) : CourseSourceSelection
}

data class LevelSelectItem<T : Any>(
  val reference: String,
  val playableLevel: T,
  val courseId: Long,
  val mode: TrainingFlow,
  val progressionGroupId: String,
  val backendSortOrder: Int,
  val sections: List<CourseSection>,
  val isReadOnly: Boolean = true,
)

data class CourseLevelBrowserState<T : Any>(
  val courses: List<CourseSummary> = emptyList(),
  val isDiscoveringCourses: Boolean = false,
  val courseDiscoveryError: String? = null,
  val selection: CourseSourceSelection = CourseSourceSelection.MyLevels,
  val mode: CourseMode? = null,
  val selectedGroupId: String? = null,
  val levels: List<LevelSelectItem<T>> = emptyList(),
  val total: Long = 0,
  val nextOffset: Int = 0,
  val isLoadingLevels: Boolean = false,
  val isLoadingMore: Boolean = false,
  val levelsError: String? = null,
) {
  val groups: List<ProgressionGroup> get() = mode?.groups.orEmpty()
  val isRemote: Boolean get() = selection is CourseSourceSelection.Course
  val canLoadMore: Boolean
    get() = isRemote && !isLoadingLevels && !isLoadingMore && levelsError == null &&
      levels.size.toLong() < total && nextOffset < total
}

/** Owns cancellable metadata/group selection and group-scoped paging for one level-select screen. */
class CourseLevelBrowser<T : Any>(
  private val mode: TrainingFlow,
  private val repository: CourseRepository,
  private val scope: CoroutineScope,
  private val extract: (PlayableTrainingLevel) -> T?,
) {
  private val _state = MutableStateFlow(CourseLevelBrowserState<T>())
  val state = _state.asStateFlow()

  private val selectedGroupByCourse = mutableMapOf<Long, String>()
  private var discoveryJob: Job? = null
  private var selectionJob: Job? = null
  private var pagingJob: Job? = null
  private var generation: Long = 0

  fun refreshCourses() {
    discoveryJob?.cancel()
    discoveryJob = scope.launch {
      _state.update { it.copy(isDiscoveringCourses = true, courseDiscoveryError = null) }
      try {
        val discovered = linkedMapOf<Long, CourseSummary>()
        var offset = 0
        var total = Long.MAX_VALUE
        while (offset.toLong() < total) {
          val page = repository.listCourses(limit = DiscoveryPageSize, offset = offset)
          total = page.meta.total
          page.courses.forEach { discovered[it.id] = it }
          val next = page.meta.offset + page.meta.limit
          if (page.courses.isEmpty() || next <= offset) break
          offset = next
        }
        val courses = discovered.values
          .filter { it.contains(mode) }
          .sortedWith(compareBy(CourseSummary::sortOrder, CourseSummary::name, CourseSummary::id))
        _state.update { current ->
          val selected = current.selection as? CourseSourceSelection.Course
          val selection = selected?.takeIf { choice -> courses.any { it.id == choice.courseId } }
            ?: CourseSourceSelection.MyLevels
          current.copy(
            courses = courses,
            isDiscoveringCourses = false,
            courseDiscoveryError = null,
            selection = selection,
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update {
          it.copy(
            isDiscoveringCourses = false,
            courseDiscoveryError = error.message ?: "Couldn't load public courses.",
          )
        }
      }
    }
  }

  fun selectMyLevels() {
    generation += 1
    selectionJob?.cancel()
    pagingJob?.cancel()
    _state.update {
      it.copy(
        selection = CourseSourceSelection.MyLevels,
        mode = null,
        selectedGroupId = null,
        levels = emptyList(),
        total = 0,
        nextOffset = 0,
        isLoadingLevels = false,
        isLoadingMore = false,
        levelsError = null,
      )
    }
  }

  fun selectCourse(courseId: Long) {
    require(_state.value.courses.any { it.id == courseId }) { "Unknown course $courseId." }
    generation += 1
    val requestGeneration = generation
    selectionJob?.cancel()
    pagingJob?.cancel()
    _state.update {
      it.copy(
        selection = CourseSourceSelection.Course(courseId),
        mode = null,
        selectedGroupId = null,
        levels = emptyList(),
        total = 0,
        nextOffset = 0,
        isLoadingLevels = true,
        isLoadingMore = false,
        levelsError = null,
      )
    }
    selectionJob = scope.launch {
      try {
        val metadata = repository.getCourseMode(courseId, mode)
        if (requestGeneration != generation) return@launch
        val remembered = selectedGroupByCourse[courseId]
        val selectedGroup = metadata.groups.firstOrNull { it.id == remembered }
          ?: metadata.groups.firstOrNull()
        selectedGroup?.let { selectedGroupByCourse[courseId] = it.id }
        _state.update {
          it.copy(
            mode = metadata,
            selectedGroupId = selectedGroup?.id,
            isLoadingLevels = selectedGroup != null,
            levelsError = null,
          )
        }
        if (selectedGroup == null) {
          _state.update { it.copy(isLoadingLevels = false) }
        } else {
          fetchPage(courseId, selectedGroup.id, offset = 0, requestGeneration, initial = true)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        if (requestGeneration == generation) {
          _state.update {
            it.copy(
              isLoadingLevels = false,
              isLoadingMore = false,
              levelsError = error.message ?: "Couldn't load the course.",
            )
          }
        }
      }
    }
  }

  fun selectGroup(groupId: String) {
    val selection = _state.value.selection as? CourseSourceSelection.Course ?: return
    if (_state.value.groups.none { it.id == groupId }) return
    if (_state.value.selectedGroupId == groupId && _state.value.levels.isNotEmpty()) return
    selectedGroupByCourse[selection.courseId] = groupId
    generation += 1
    val requestGeneration = generation
    selectionJob?.cancel()
    pagingJob?.cancel()
    _state.update {
      it.copy(
        selectedGroupId = groupId,
        levels = emptyList(),
        total = 0,
        nextOffset = 0,
        isLoadingLevels = true,
        isLoadingMore = false,
        levelsError = null,
      )
    }
    selectionJob = scope.launch {
      try {
        fetchPage(selection.courseId, groupId, 0, requestGeneration, initial = true)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        if (requestGeneration == generation) {
          _state.update {
            it.copy(isLoadingLevels = false, levelsError = error.message ?: "Couldn't load the group.")
          }
        }
      }
    }
  }

  fun loadNextPage() {
    val current = _state.value
    if (!current.canLoadMore) return
    val selection = current.selection as? CourseSourceSelection.Course ?: return
    val groupId = current.selectedGroupId ?: return
    val requestGeneration = generation
    pagingJob?.cancel()
    pagingJob = scope.launch {
      _state.update { it.copy(isLoadingMore = true) }
      try {
        fetchPage(selection.courseId, groupId, current.nextOffset, requestGeneration, initial = false)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        if (requestGeneration == generation) {
          _state.update {
            it.copy(
              isLoadingMore = false,
              levelsError = error.message ?: "Couldn't load more levels.",
            )
          }
        }
      }
    }
  }

  fun retryLevels() {
    val current = _state.value
    val selection = current.selection as? CourseSourceSelection.Course ?: return
    if (current.mode == null) {
      selectCourse(selection.courseId)
      return
    }
    val groupId = current.selectedGroupId ?: return
    if (current.levels.isEmpty()) {
      selectGroupAfterError(selection.courseId, groupId)
    } else {
      _state.update { it.copy(levelsError = null) }
      loadNextPage()
    }
  }

  private fun selectGroupAfterError(courseId: Long, groupId: String) {
    generation += 1
    val requestGeneration = generation
    selectionJob?.cancel()
    pagingJob?.cancel()
    _state.update { it.copy(isLoadingLevels = true, levelsError = null, nextOffset = 0) }
    selectionJob = scope.launch {
      try {
        fetchPage(courseId, groupId, 0, requestGeneration, initial = true)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        if (requestGeneration == generation) {
          _state.update { it.copy(isLoadingLevels = false, levelsError = error.message ?: "Couldn't load the group.") }
        }
      }
    }
  }

  private suspend fun fetchPage(
    courseId: Long,
    groupId: String,
    offset: Int,
    requestGeneration: Long,
    initial: Boolean,
  ) {
    val page = repository.getLevels(courseId, mode, groupId, PageSize, offset)
    if (requestGeneration != generation) return
    val mapped = page.levels.map(::presentation)
    _state.update { current ->
      val combined = if (initial) mapped else current.levels + mapped
      val deduplicated = combined.distinctBy { it.reference }
      current.copy(
        levels = deduplicated,
        total = page.meta.total,
        nextOffset = page.meta.offset + page.meta.limit,
        isLoadingLevels = false,
        isLoadingMore = false,
        levelsError = null,
      )
    }
  }

  private fun presentation(item: CourseLevelItem): LevelSelectItem<T> {
    val level = extract(item.playable)
      ?: error("The backend returned ${item.mode} content to the ${mode.name} level browser.")
    return LevelSelectItem(
      reference = item.reference.encoded,
      playableLevel = level,
      courseId = item.sourceCourseId,
      mode = item.mode,
      progressionGroupId = item.progressionGroupId,
      backendSortOrder = item.sortOrder,
      sections = item.sections,
    )
  }

  private companion object {
    const val PageSize = 20
    const val DiscoveryPageSize = 50
  }
}
