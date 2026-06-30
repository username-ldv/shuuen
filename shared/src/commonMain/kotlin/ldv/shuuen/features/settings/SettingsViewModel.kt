package ldv.shuuen.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.MusicLabelDefaults
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.settings.SettingsRepository
import kotlin.time.Duration.Companion.milliseconds

class SettingsViewModel(
    private val midiEngine: MidiEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
  private val mutableState = MutableStateFlow(SettingsUiState())
  val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

  private var previewJob: Job? = null

  init {
    viewModelScope.launch {
      settingsRepository.settings.collect { settings ->
        mutableState.update {
          it.copy(
            selectedPresets = settings.presets,
            selectedVolumes = settings.volumes,
            melodyOriginalVolumeBoost = settings.melodyOriginalVolumeBoost,
            inputMethod = settings.inputMethod,
            allowSevenAccidentalKeys = settings.allowSevenAccidentalKeys,
            musicLabels = settings.musicLabels,
          )
        }
      }
    }
    viewModelScope.launch {
      when (val status = midiEngine.initialize()) {
        MidiEngineStatus.Ready -> {
          val grouped = midiEngine.availablePresets().groupBy { it.bank }
          val soundbanks =
              grouped.keys.sorted().map { bank ->
                Soundbank(bank, grouped.getValue(bank).sortedBy { it.id })
              }
          mutableState.update {
            it.copy(
                audioReady = true,
                loadingPresets = false,
                soundbanks = soundbanks,
                errorMessage = null,
            )
          }
        }

        is MidiEngineStatus.Failed -> {
          mutableState.update {
            it.copy(audioReady = false, loadingPresets = false, errorMessage = status.message)
          }
        }
      }
    }
  }

  fun onAction(action: SettingsAction) {
    when (action) {
      is SettingsAction.SelectInputMethod -> {
        viewModelScope.launch { settingsRepository.setInputMethod(action.inputMethod) }
      }

      is SettingsAction.SetAllowSevenAccidentalKeys -> {
        viewModelScope.launch { settingsRepository.setAllowSevenAccidentalKeys(action.value) }
      }

      is SettingsAction.OpenLabelEditor ->
        mutableState.update { it.copy(openLabelEditor = action.editor) }

      SettingsAction.CloseLabelEditor ->
        mutableState.update { it.copy(openLabelEditor = null) }

      is SettingsAction.SetNoteName -> setNoteName(action.index, action.value)

      is SettingsAction.SetDegreeName -> setDegreeName(action.index, action.value)

      is SettingsAction.OpenPicker ->
          mutableState.update { it.copy(openPickerChannel = action.channel) }

      SettingsAction.ClosePicker -> {
        previewJob?.cancel()
        midiEngine.stopAll()
        mutableState.update { it.copy(openPickerChannel = null) }
      }

      is SettingsAction.SelectPreset -> selectPreset(action.channel, action.preset)
      is SettingsAction.Preview -> preview(action.channel)
      is SettingsAction.SetVolume -> midiEngine.setVolume(action.channel, action.value)
      is SettingsAction.CommitVolume -> commitVolume(action.channel, action.value)
      is SettingsAction.SetMelodyOriginalVolumeBoost ->
        mutableState.update { it.copy(melodyOriginalVolumeBoost = action.value.coerceIn(0, 127)) }

      is SettingsAction.CommitMelodyOriginalVolumeBoost ->
        commitMelodyOriginalVolumeBoost(action.value)
    }
  }

  private fun setNoteName(index: Int, value: String) {
    val next =
      mutableState.value.musicLabels.noteNames.withLabel(
        index = index,
        value = value,
        defaults = MusicLabelDefaults.NoteNames,
      ) ?: return

    viewModelScope.launch { settingsRepository.setNoteNames(next) }
  }

  private fun setDegreeName(index: Int, value: String) {
    val next =
      mutableState.value.musicLabels.degreeNames.withLabel(
        index = index,
        value = value,
        defaults = MusicLabelDefaults.DegreeNames,
      ) ?: return

    viewModelScope.launch { settingsRepository.setDegreeNames(next) }
  }

  private fun selectPreset(channel: MidiChannel, preset: Preset) {
    midiEngine.setPreset(channel, preset)
    viewModelScope.launch { settingsRepository.setPreset(channel, preset) }
  }

  private fun commitVolume(channel: MidiChannel, value: Int) {
    midiEngine.setVolume(channel, value)
    viewModelScope.launch { settingsRepository.setVolume(channel, value) }
  }

  private fun commitMelodyOriginalVolumeBoost(value: Int) {
    val coerced = value.coerceIn(0, 127)
    mutableState.update { it.copy(melodyOriginalVolumeBoost = coerced) }
    viewModelScope.launch { settingsRepository.setMelodyOriginalVolumeBoost(coerced) }
  }

  /** Auditions the channel's current preset with a short phrase. */
  private fun preview(channel: MidiChannel) {
    if (!mutableState.value.audioReady) return
    previewJob?.cancel()
    previewJob = viewModelScope.launch {
      midiEngine.stopAll(channel)
      when {
        channel == MidiChannel.Drone || channel == MidiChannel.Cadence -> {
          val chord = Chord.major(Note(Pitch.random(), octave = (3..4).random()))
          midiEngine.playChord(chord, channel)
          try {
            delay(1200.milliseconds)
          } finally {
            midiEngine.stopChord(chord, channel)
          }
        }

        else -> {
          val note = Note(Pitch.random(), octave = (2..7).random())
          midiEngine.playNote(note, channel)
          try {
            delay(1400.milliseconds)
          } finally {
            midiEngine.stopNote(note, channel)
          }
        }
      }
    }
  }

  override fun onCleared() {
    midiEngine.stopAll()
  }
}

private fun List<String>.withLabel(
  index: Int,
  value: String,
  defaults: List<String>,
): List<String>? {
  if (index !in defaults.indices) return null
  return List(defaults.size) { itemIndex ->
    when (itemIndex) {
      index -> value
      else -> getOrNull(itemIndex) ?: defaults[itemIndex]
    }
  }
}
