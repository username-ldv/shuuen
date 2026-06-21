package ldv.shuuen.features.training.melodies.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
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
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesSession

enum class MelodiesSourceMode {
  Random,
  Midi,
}

data class MelodiesSetupState(
  val sourceMode: MelodiesSourceMode = MelodiesSourceMode.Random,
  val loadedMidiName: String? = null,
  val midiBytes: ByteArray? = null,
  val isLoadingMidi: Boolean = false,
  val midiError: String? = null,
  val useOriginalVelocities: Boolean = false,
  val context: DegreeContext? = null,
) {
  /** A melody can be started once a MIDI file has been loaded. */
  val canStart: Boolean
    get() = sourceMode == MelodiesSourceMode.Midi && midiBytes != null
}

@OptIn(ExperimentalUuidApi::class)
class MelodiesSetupScreenViewModel(
  private val session: MelodiesSession,
) : ViewModel() {
  private val _state = MutableStateFlow(MelodiesSetupState())
  val state = _state.asStateFlow()

  fun selectSourceMode(mode: MelodiesSourceMode) {
    _state.update { it.copy(sourceMode = mode) }
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
      val bytes = runCatching { file.readBytes() }.getOrNull()
      if (bytes == null || bytes.isEmpty()) {
        _state.update { it.copy(isLoadingMidi = false, midiError = "Couldn't read ${file.name}.") }
        return@launch
      }
      Napier.v { "Loaded ${bytes.size} bytes from ${file.name}" }
      _state.update {
        it.copy(
          sourceMode = MelodiesSourceMode.Midi,
          loadedMidiName = file.name,
          midiBytes = bytes,
          isLoadingMidi = false,
          midiError = null,
        )
      }
    }
  }

  fun updateContext(context: DegreeContext) {
    Napier.v { "Melodies setup: updating context to $context" }
    _state.update { it.copy(context = context) }
  }

  /** Builds the melody and stages it for the play screen. Returns false if nothing is ready yet. */
  fun stageLevelForTraining(): Boolean {
    val current = _state.value
    val bytes = current.midiBytes ?: return false
    val level =
      MelodiesLevel(
        id = Uuid.generateV7().toString(),
        name = current.loadedMidiName?.substringBeforeLast('.') ?: "Melody",
        midiBytes = bytes,
        useOriginalVelocities = current.useOriginalVelocities,
        context = current.context,
        source = LevelSource.Imported,
      )
    session.stage(level)
    Napier.v { "Staged melody level '${level.name}'" }
    return true
  }
}
