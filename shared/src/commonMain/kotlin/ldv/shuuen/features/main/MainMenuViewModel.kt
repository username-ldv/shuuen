package ldv.shuuen.features.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.core.sync.DataSyncResult
import ldv.shuuen.core.sync.DataSyncStatus
import ldv.shuuen.core.sync.LevelSyncRepository
import ldv.shuuen.core.sync.TrainingSessionSyncRepository
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository

data class MainMenuState(
  val continueCard: ContinueCardState? = null,
  val dataSyncStatus: DataSyncStatus = DataSyncStatus.Idle,
)

data class ContinueCardState(
  val flow: TrainingFlow,
  val levelReference: String,
  val levelName: String,
  val isCourseLevel: Boolean = false,
  val isLoadingCourse: Boolean = false,
  val course: ContinueCourseProgress? = null,
  val nextLevelReference: String? = null,
)

data class ContinueCourseProgress(
  val name: String,
  val currentGroup: ContinueGroupProgress?,
  val groups: List<ContinueGroupProgress>,
) {
  val total: CompletionProgress
    get() = CompletionProgress(
      completed = groups.sumOf { it.progress.completed },
      total = groups.sumOf { it.progress.total },
    )
}

data class ContinueGroupProgress(
  val id: String,
  val name: String,
  val progress: CompletionProgress,
)

data class CompletionProgress(
  val completed: Long,
  val total: Long,
) {
  init {
    require(completed >= 0) { "Completed level count cannot be negative." }
    require(total >= 0) { "Total level count cannot be negative." }
  }

  val fraction: Float
    get() = if (total == 0L) 0f else (completed.toDouble() / total).toFloat().coerceIn(0f, 1f)

  val percentage: Int
    get() = (fraction * 100).roundToInt()
}

class MainMenuViewModel(
  private val trainingSessionRepository: TrainingSessionRepository,
  private val courseRepository: CourseRepository,
  private val levelSyncRepository: LevelSyncRepository,
  private val trainingSessionSyncRepository: TrainingSessionSyncRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(MainMenuState())
  val state = _state.asStateFlow()

  init {
    observeContinueTarget()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeContinueTarget() {
    viewModelScope.launch {
      trainingSessionRepository.observeLatestSession()
        .flatMapLatest { latest ->
          if (latest == null) {
            flowOf(ContinueSnapshot())
          } else {
            trainingSessionRepository.observeCompletedLevelIds(latest.flow)
              .map { completed ->
                ContinueSnapshot(
                  latest = latest,
                  completedLevelIds =
                    if (latest.finishedEarly) completed else completed + latest.levelId,
                )
              }
          }
        }
        .collectLatest { snapshot ->
          val latest = snapshot.latest
          if (latest == null) {
            _state.update { it.copy(continueCard = null) }
            return@collectLatest
          }

          val remoteReference =
            runCatching { LevelReference.decode(latest.levelId) }.getOrNull()
              as? LevelReference.Remote
          val baseCard = latest.toContinueCard(remoteReference != null)
          _state.update { it.copy(continueCard = baseCard) }
          if (remoteReference == null) return@collectLatest

          try {
            val courseCard = loadCourseProgress(latest, remoteReference, snapshot.completedLevelIds)
            _state.update { current -> current.copy(continueCard = courseCard) }
          } catch (error: CancellationException) {
            throw error
          } catch (error: Throwable) {
            Napier.w(error) { "Couldn't load main-menu course progress" }
            _state.update { current ->
              val card = current.continueCard
              if (card?.levelReference == latest.levelId) {
                current.copy(continueCard = card.copy(isLoadingCourse = false))
              } else {
                current
              }
            }
          }
        }
    }
  }

  fun syncData() {
    if (_state.value.dataSyncStatus == DataSyncStatus.Syncing) return
    viewModelScope.launch {
      _state.update { it.copy(dataSyncStatus = DataSyncStatus.Syncing) }
      try {
        val levels = levelSyncRepository.sync()
        val trainingSessions = trainingSessionSyncRepository.sync()
        val result = DataSyncResult(levels = levels, trainingSessions = trainingSessions)
        _state.update { it.copy(dataSyncStatus = DataSyncStatus.Complete(result)) }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        Napier.w(error) { "Couldn't sync data" }
        _state.update {
          it.copy(
            dataSyncStatus =
              DataSyncStatus.Failed(error.message ?: "Couldn't sync data."),
          )
        }
      }
    }
  }

  private suspend fun loadCourseProgress(
    latest: TrainingSession,
    reference: LevelReference.Remote,
    completedLevelIds: Set<String>,
  ): ContinueCardState {
    require(reference.mode == latest.flow) { "The latest course level has a mismatched flow." }
    val course = courseRepository.getCourse(reference.courseId)
    val mode = courseRepository.getCourseMode(reference.courseId, reference.mode)
    val latestItem = courseRepository.getLevel(reference)

    val completedRemoteIds =
      completedLevelIds.mapNotNull { encoded ->
        val completed = runCatching { LevelReference.decode(encoded) }.getOrNull()
          as? LevelReference.Remote
        completed?.takeIf {
          it.courseId == reference.courseId && it.mode == reference.mode
        }?.levelId
      }.distinct()

    val completedItems =
      completedRemoteIds.chunked(CourseQueryLimit).flatMap { ids ->
        courseRepository.queryLevels(reference.courseId, reference.mode, ids).levels
      }.distinctBy { it.reference.encoded }
    val completedByGroup = completedItems.groupingBy { it.progressionGroupId }.eachCount()
    val groups =
      mode.groups.sortedWith(compareBy({ it.sortOrder }, { it.name }, { it.id })).map { group ->
        ContinueGroupProgress(
          id = group.id,
          name = group.name,
          progress = CompletionProgress(
            completed = (completedByGroup[group.id] ?: 0).toLong().coerceAtMost(group.levelCount),
            total = group.levelCount,
          ),
        )
      }
    val nextReference =
      latestItem.navigation?.nextLevelId?.let { nextLevelId ->
        LevelReference.Remote(reference.courseId, reference.mode, nextLevelId).encoded
      }

    return latest.toContinueCard(isCourseLevel = true).copy(
      isLoadingCourse = false,
      course = ContinueCourseProgress(
        name = course.name,
        currentGroup = groups.firstOrNull { it.id == latestItem.progressionGroupId },
        groups = groups,
      ),
      nextLevelReference = nextReference,
    )
  }

  private fun TrainingSession.toContinueCard(isCourseLevel: Boolean) =
    ContinueCardState(
      flow = flow,
      levelReference = levelId,
      levelName = levelName,
      isCourseLevel = isCourseLevel,
      isLoadingCourse = isCourseLevel,
    )

  private data class ContinueSnapshot(
    val latest: TrainingSession? = null,
    val completedLevelIds: Set<String> = emptySet(),
  )

  private companion object {
    const val CourseQueryLimit = 200
  }
}
