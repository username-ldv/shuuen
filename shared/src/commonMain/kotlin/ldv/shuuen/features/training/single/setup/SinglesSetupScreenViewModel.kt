package ldv.shuuen.features.training.single.setup

import androidx.lifecycle.ViewModel
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.toNoteRange
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.common.asConfigDegreeStates

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
              range = NoteRange(Note(Pitch.C, 2), Note(Pitch.C, 7)),
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

  fun changeRotateEveryQuestions(v: Int?) {
    _singlesLevelState.update {
      val levelConfig =
          when (val config = it.levelConfig) {
            is LevelConfig.Singles.Absolute -> config.copy(rotateEveryQuestions = v)
            is LevelConfig.Singles.Relative -> config.copy(rotateEveryQuestions = v)
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
