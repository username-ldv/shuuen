package ldv.shuuen.features.training.melodies.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
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
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.core.music.toNoteRange
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.common.asConfigDegreeStates
import ldv.shuuen.features.training.common.components.TuneInconsistencyRange
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository

enum class MelodiesSourceMode {
  Random,
  Midi,
}

data class MelodiesSetupState(
  val sourceMode: MelodiesSourceMode = MelodiesSourceMode.Random,
  val scaleConfig: ScaleConfig =
    ScaleConfig.RelativeScaleConfig(
      scaleType = ScaleType.Major,
      degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
    ),
  val context: DegreeContext? = null,
  /** null means an unlimited session. */
  val questionsNumber: Int? = 20,
  val notesPerSequence: Int = 4,
  /** When on, sequences are ignored and notes stream endlessly; questions/rotation don't apply. */
  val endlessNotes: Boolean = false,
  /** Move to a new random tonic every this many questions; null is off. Relative scales only. */
  val rotateEveryQuestions: Int? = 10,
  val tempo: Int = 96,
  /** Each note plays randomly out of tune by up to ± this many cents; 0 is off. */
  val tuneInconsistencyCents: Int = 0,
  /** Rhythm figures + note-picker weights of the generated melodies. */
  val melodyStyle: MelodyStyle = MelodyStyles.Default,
  val range: NoteRange = NoteRange(Note(Pitch.C, 2), Note(Pitch.C, 7)),
  val loadedMidi: PlatformFile? = null,
  val loadedMidiName: String? = null,
  val isLoadingMidi: Boolean = false,
  val midiError: String? = null,
  val useOriginalVelocities: Boolean = false,
  /** Optional audio (e.g. MP3 of the real song) played in sync with the MIDI melody. */
  val loadedBacking: PlatformFile? = null,
  val loadedBackingName: String? = null,
  val isLoadingBacking: Boolean = false,
  /** Position in the backing audio (ms) matching the MIDI's start; negative when audio lags. */
  val backingOffsetMs: Long = 0,
)

@OptIn(ExperimentalUuidApi::class)
class MelodiesSetupScreenViewModel(
  editLevelId: String,
  private val levelRepository: MelodiesLocalLevelRepository,
) : ViewModel() {
  private val editedLevelId = editLevelId.takeIf { it.isNotBlank() }
  private var currentLevelId = Uuid.generateV7().toString()
  val isEditing = editedLevelId != null

  private val _state = MutableStateFlow(MelodiesSetupState())
  val state = _state.asStateFlow()

  init {
    editedLevelId?.let { levelId ->
      viewModelScope.launch {
        levelRepository.getLevelById(levelId).collect { response ->
          when (response) {
            is ResponseState.Success -> {
              currentLevelId = response.result.id
              _state.value = response.result.toSetupState(_state.value)
            }

            is ResponseState.Error ->
              Napier.w(response.throwable) { "Couldn't load melodies level for editing" }

            is ResponseState.Loading -> Unit
          }
        }
      }
    }
  }

  fun selectSourceMode(mode: MelodiesSourceMode) {
    _state.update { it.copy(sourceMode = mode) }
  }

  fun changeScale(scaleConfig: ScaleConfig) {
    _state.update { it.copy(scaleConfig = scaleConfig) }
  }

  fun changeQuestionsNumber(v: Int?) {
    _state.update { it.copy(questionsNumber = v) }
  }

  fun changeNotesPerSequence(v: Int) {
    if (v < 1) return
    _state.update { it.copy(notesPerSequence = v) }
  }

  fun setEndlessNotes(v: Boolean) {
    _state.update { it.copy(endlessNotes = v) }
  }

  fun changeRotateEveryQuestions(v: Int?) {
    _state.update { it.copy(rotateEveryQuestions = v) }
  }

  fun changeTempo(v: Int) {
    _state.update { it.copy(tempo = v.coerceIn(TempoRange)) }
  }

  fun changeTuneInconsistency(v: Int) {
    _state.update { it.copy(tuneInconsistencyCents = v.coerceIn(TuneInconsistencyRange)) }
  }

  fun changeMelodyStyle(style: MelodyStyle) {
    _state.update { it.copy(melodyStyle = style) }
  }

  fun changeRangeStart(v: Note) {
    _state.update { it.copy(range = (v to it.range.to).toNoteRange()) }
  }

  fun changeRangeEnd(v: Note) {
    _state.update { it.copy(range = (it.range.from to v).toNoteRange()) }
  }

  fun setUseOriginalVelocities(value: Boolean) {
    _state.update { it.copy(useOriginalVelocities = value) }
  }

  fun loadMidiFile() {
    _state.update { it.copy(isLoadingMidi = true, midiError = null) }
    viewModelScope.launch {
      val file = FileKit.openFilePicker(type = FileKitType.File(listOf("mid", "midi")))
      if (file == null) {
        _state.update { it.copy(isLoadingMidi = false) }
        return@launch
      }
      // Only the file reference is stored with the level; the read here just validates the pick.
      val bytes = runCatching { file.readBytes() }.getOrNull()
      if (bytes == null || bytes.isEmpty()) {
        _state.update { it.copy(isLoadingMidi = false, midiError = "Couldn't read ${file.name}.") }
        return@launch
      }
      _state.update {
        it.copy(
          sourceMode = MelodiesSourceMode.Midi,
          loadedMidi = file,
          loadedMidiName = file.name,
          isLoadingMidi = false,
          midiError = null,
        )
      }
    }
  }

  fun loadBackingFile() {
    _state.update { it.copy(isLoadingBacking = true, midiError = null) }
    viewModelScope.launch {
      val file = FileKit.openFilePicker(type = FileKitType.File(listOf("mp3", "ogg", "wav")))
      if (file == null) {
        _state.update { it.copy(isLoadingBacking = false) }
        return@launch
      }
      // Like the MIDI pick, only the reference is stored; the read just validates the pick.
      val bytes = runCatching { file.readBytes() }.getOrNull()
      if (bytes == null || bytes.isEmpty()) {
        _state.update {
          it.copy(isLoadingBacking = false, midiError = "Couldn't read ${file.name}.")
        }
        return@launch
      }
      _state.update {
        it.copy(
          loadedBacking = file,
          loadedBackingName = file.name,
          isLoadingBacking = false,
          midiError = null,
        )
      }
    }
  }

  fun clearBackingFile() {
    _state.update { it.copy(loadedBacking = null, loadedBackingName = null) }
  }

  fun changeBackingOffsetMs(value: Long) {
    _state.update { it.copy(backingOffsetMs = value.coerceIn(BackingOffsetRangeMs)) }
  }

  fun updateContext(context: DegreeContext) {
    Napier.v { "Melodies setup: updating context to $context" }
    _state.update { it.copy(context = context) }
  }

  /** Saves the configured level. Returns false when the level is not ready to save yet. */
  suspend fun upsertLevel(): Boolean {
    val current = _state.value
    val config =
      when (current.sourceMode) {
        MelodiesSourceMode.Random ->
          LevelConfig.Melodies.Random(
            scaleConfig = current.scaleConfig,
            questionsNumber = if (current.endlessNotes) null else current.questionsNumber,
            notesPerSequence = if (current.endlessNotes) null else current.notesPerSequence,
            tempo = current.tempo,
            tuneInconsistencyCents = current.tuneInconsistencyCents,
            range = current.range,
            melodyStyle = current.melodyStyle,
            rotateEveryQuestions =
              current.rotateEveryQuestions.takeIf {
                current.scaleConfig is ScaleConfig.RelativeScaleConfig && !current.endlessNotes
              },
          )

        MelodiesSourceMode.Midi -> {
          val file = current.loadedMidi
          if (file == null) {
            _state.update { it.copy(midiError = "Load a .midi file first.") }
            return false
          }
          LevelConfig.Melodies.Midi(
            file = file,
            fileName = current.loadedMidiName ?: file.name,
            useOriginalVelocities = current.useOriginalVelocities,
            backingFile = current.loadedBacking,
            backingFileName = current.loadedBacking?.let { current.loadedBackingName ?: it.name },
            backingOffsetMs = if (current.loadedBacking != null) current.backingOffsetMs else 0,
          )
        }
      }
    val level =
      MelodiesLevel(
        id = currentLevelId,
        name = defaultLevelName(config),
        config = config,
        context = current.context,
        source =
          when (config) {
            is LevelConfig.Melodies.Random -> LevelSource.User
            is LevelConfig.Melodies.Midi -> LevelSource.Imported
          },
      )
    levelRepository.upsertLevel(level)
    Napier.v { "Saved melodies level '${level.name}'" }
    return true
  }

  private fun MelodiesLevel.toSetupState(current: MelodiesSetupState): MelodiesSetupState =
    when (val levelConfig = config) {
      is LevelConfig.Melodies.Random ->
        current.copy(
          sourceMode = MelodiesSourceMode.Random,
          scaleConfig = levelConfig.scaleConfig,
          context = context,
          questionsNumber = levelConfig.questionsNumber,
          notesPerSequence = levelConfig.notesPerSequence ?: current.notesPerSequence,
          endlessNotes = levelConfig.notesPerSequence == null,
          rotateEveryQuestions = levelConfig.rotateEveryQuestions,
          tempo = levelConfig.tempo,
          tuneInconsistencyCents = levelConfig.tuneInconsistencyCents,
          melodyStyle = levelConfig.melodyStyle,
          range = levelConfig.range,
          loadedMidi = null,
          loadedMidiName = null,
          isLoadingMidi = false,
          midiError = null,
        )

      is LevelConfig.Melodies.Midi ->
        current.copy(
          sourceMode = MelodiesSourceMode.Midi,
          context = context,
          loadedMidi = levelConfig.file,
          loadedMidiName = levelConfig.fileName,
          isLoadingMidi = false,
          midiError = null,
          useOriginalVelocities = levelConfig.useOriginalVelocities,
          loadedBacking = levelConfig.backingFile,
          loadedBackingName = levelConfig.backingFileName,
          isLoadingBacking = false,
          backingOffsetMs = levelConfig.backingOffsetMs,
        )
    }

  private fun defaultLevelName(config: LevelConfig.Melodies): String =
    when (config) {
      is LevelConfig.Melodies.Random ->
        when (val scale = config.scaleConfig) {
          is ScaleConfig.AbsoluteScaleConfig -> "${scale.root} ${scale.scaleType}"
          is ScaleConfig.RelativeScaleConfig -> "Random ${scale.scaleType}"
        }

      is LevelConfig.Melodies.Midi -> config.fileName.substringBeforeLast('.')
    }

  companion object {
    val TempoRange = 20..360

    /** ±10 minutes: any sane lead-in fits, and typos can't push the backing out of reach. */
    val BackingOffsetRangeMs = -600_000L..600_000L
  }
}
