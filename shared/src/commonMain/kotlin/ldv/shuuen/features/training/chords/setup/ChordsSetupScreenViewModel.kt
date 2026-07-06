package ldv.shuuen.features.training.chords.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.generator.ChordStyle
import ldv.shuuen.core.music.toNoteRange
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository
import ldv.shuuen.features.training.common.asConfigDegreeStates
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig

class ChordsSetupScreenViewModel(
    editLevelId: String,
    val levelRepository: ChordsLocalLevelRepository,
) : ViewModel() {
  private val editedLevelId = editLevelId.takeIf { it.isNotBlank() }
  val isEditing = editedLevelId != null

  @OptIn(ExperimentalUuidApi::class)
  private val _chordsLevelState =
      MutableStateFlow(
          ChordsLevel(
              id = Uuid.generateV7().toString(),
              name = "",
              levelConfig =
                  LevelConfig.Chords.Relative(
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
              chordSize = ChordSizeRange(min = 2, max = 3),
              sustainNotes = true,
              answerOrder = ChordAnswerOrder.Any,
          )
      )
  val screenState = _chordsLevelState.asStateFlow()

  init {
    editedLevelId?.let { levelId ->
      viewModelScope.launch {
        levelRepository.getLevelById(levelId).collect { response ->
          when (response) {
            is ResponseState.Success -> _chordsLevelState.value = response.result
            is ResponseState.Error ->
                Napier.w(response.throwable) { "Couldn't load chords level for editing" }
            is ResponseState.Loading -> Unit
          }
        }
      }
    }
  }

  fun changeQuestionsNumber(v: Int?) {
    _chordsLevelState.update { it.copy(questionsNumber = v) }
  }

  fun changeRangeStart(v: Note) {
    _chordsLevelState.update { it.copy(range = (v to it.range.to).toNoteRange()) }
  }

  fun changeRangeEnd(v: Note) {
    _chordsLevelState.update { it.copy(range = (it.range.from to v).toNoteRange()) }
  }

  /** Moves one end of the chord-size range, dragging the other end along when they'd cross. */
  fun changeChordSizeMin(v: Int) {
    val min = v.coerceIn(ChordSizeRange.MinSize, ChordSizeRange.MaxSize)
    _chordsLevelState.update {
      it.copy(chordSize = ChordSizeRange(min, maxOf(min, it.chordSize.max)))
    }
  }

  fun changeChordSizeMax(v: Int) {
    val max = v.coerceIn(ChordSizeRange.MinSize, ChordSizeRange.MaxSize)
    _chordsLevelState.update {
      it.copy(chordSize = ChordSizeRange(minOf(max, it.chordSize.min), max))
    }
  }

  fun changeSustainNotes(v: Boolean) {
    _chordsLevelState.update { it.copy(sustainNotes = v) }
  }

  fun changeAnswerOrder(v: ChordAnswerOrder) {
    _chordsLevelState.update { it.copy(answerOrder = v) }
  }

  fun changeChordStyle(v: ChordStyle) {
    _chordsLevelState.update {
      val levelConfig =
          when (val config = it.levelConfig) {
            is LevelConfig.Chords.Absolute -> config.copy(chordStyle = v)
            is LevelConfig.Chords.Relative -> config.copy(chordStyle = v)
          }
      it.copy(levelConfig = levelConfig)
    }
  }

  fun changeScale(scaleConfig: ScaleConfig) {
    _chordsLevelState.update {
      val levelConfig =
          when (scaleConfig) {
            is ScaleConfig.AbsoluteScaleConfig ->
                LevelConfig.Chords.Absolute(
                    scales = listOf(scaleConfig),
                    chordStyle = it.levelConfig.chordStyle,
                )

            is ScaleConfig.RelativeScaleConfig ->
                LevelConfig.Chords.Relative(
                    scaleConfig = scaleConfig,
                    rotateEveryQuestions = it.levelConfig.rotateEveryQuestions,
                    chordStyle = it.levelConfig.chordStyle,
                )
          }
      it.copy(levelConfig = levelConfig)
    }
  }

  fun changeRotateEveryQuestions(v: Int?) {
    _chordsLevelState.update {
      val levelConfig =
          when (val config = it.levelConfig) {
            is LevelConfig.Chords.Absolute -> config.copy(rotateEveryQuestions = v)
            is LevelConfig.Chords.Relative -> config.copy(rotateEveryQuestions = v)
          }
      it.copy(levelConfig = levelConfig)
    }
  }

  suspend fun upsertLevel() {
    val level = screenState.value
    val scaleName =
        when (val levelConfig = level.levelConfig) {
          is LevelConfig.Chords.Absolute -> {
            val scale = levelConfig.scales.first()
            "${scale.root} ${scale.scaleType}"
          }

          is LevelConfig.Chords.Relative -> {
            "Random ${levelConfig.scaleConfig.scaleType}"
          }
    }
    val levelName = "$scaleName · ${level.chordSize} notes"
    levelRepository.upsertLevel(level.copy(name = levelName))
    Napier.v { "Saved chords level: $level" }
  }

  fun updateContext(context: DegreeContext) {
    Napier.v { "updating context to $context" }
    _chordsLevelState.update { it.copy(context = context) }
  }
}
