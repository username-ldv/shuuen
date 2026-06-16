package ldv.shuuen.ui.screens.training.single.setup

import androidx.lifecycle.ViewModel
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ldv.shuuen.domain.audio.music.DegreeContext
import ldv.shuuen.domain.audio.music.Note
import ldv.shuuen.domain.audio.music.NoteRange
import ldv.shuuen.domain.audio.music.Pitch
import ldv.shuuen.domain.audio.music.Scale
import ldv.shuuen.domain.audio.music.ScaleType
import ldv.shuuen.domain.audio.music.toNoteRange
import ldv.shuuen.domain.repository.local.SinglesLocalLevelRepository
import ldv.shuuen.domain.training.level.LevelConfig
import ldv.shuuen.domain.training.level.LevelSource
import ldv.shuuen.domain.training.level.ScaleConfig
import ldv.shuuen.domain.training.singles.SinglesLevel
import ldv.shuuen.ui.screens.training.common.asConfigDegreeStates

class SinglesSetupScreenViewModel(val levelRepository: SinglesLocalLevelRepository) : ViewModel() {
  @OptIn(ExperimentalUuidApi::class)
  private val _singlesLevelState =
      MutableStateFlow(
          SinglesLevel(
              id = Uuid.generateV7().toString(),
              name = "",
              levelConfig =
                  LevelConfig.Singles.Relative(
                      scaleConfig =
                          ScaleConfig.RelativeScaleConfig(
                              scaleType = ScaleType.Major,
                              degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
                          ),
                  ),
              context = null,
              source = LevelSource.User,
              questionsNumber = 20,
              range = NoteRange(Note(Pitch.C, 2), Note(Pitch.C, 6)),
          )
      )
  val screenState = _singlesLevelState.asStateFlow()

  fun changeQuestionsNumber(v: Int?) {
    _singlesLevelState.update { it.copy(questionsNumber = v) }
  }

  fun changeRangeStart(v: Note) {
    _singlesLevelState.update { it.copy(range = (v to it.range.to).toNoteRange()) }
  }

  fun changeRangeEnd(v: Note) {
    _singlesLevelState.update { it.copy(range = (it.range.from to v).toNoteRange()) }
  }

  fun changeScale(scaleConfig: ScaleConfig) {
    _singlesLevelState.update {
      val levelConfig =
          when (scaleConfig) {
            is ScaleConfig.AbsoluteScaleConfig ->
                LevelConfig.Singles.Absolute(
                    scales = listOf(scaleConfig),
                )

            is ScaleConfig.RelativeScaleConfig ->
                LevelConfig.Singles.Relative(
                    scaleConfig = scaleConfig,
                    rotateEveryQuestions = it.levelConfig.rotateEveryQuestions,
                )
          }
      it.copy(levelConfig = levelConfig)
    }
  }

  suspend fun upsertLevel() {
    val level = screenState.value
    // todo: what should be the default name?
    val levelName =
        when (val levelConfig = level.levelConfig) {
          is LevelConfig.Singles.Absolute -> {
            val scale = levelConfig.scales.first()
            "${scale.root} ${scale.scaleType}"
          }

          is LevelConfig.Singles.Relative -> {
            "Random ${levelConfig.scaleConfig.scaleType}"
          }
        }
    levelRepository.upsertLevel(level.copy(name = levelName))
    Napier.v { "Saved new level: $level" }
  }

  fun updateContext(context: DegreeContext) {
    Napier.v { "updating context to $context" }
    _singlesLevelState.update { it.copy(context = context) }
  }

  override fun onCleared() {
    Napier.v { "Setup screen viewmodel cleared?" }
  }
}
