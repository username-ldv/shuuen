package ldv.shuuen.ui.screens.app_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.domain.audio.engine.MidiEngine
import ldv.shuuen.domain.audio.engine.MidiEngineStatus
import ldv.shuuen.domain.audio.midi.MidiChannel
import ldv.shuuen.domain.audio.midi.Preset
import ldv.shuuen.domain.audio.music.Chord
import ldv.shuuen.domain.audio.music.Note
import ldv.shuuen.domain.audio.music.Pitch
import ldv.shuuen.domain.repository.SettingsRepository
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
          it.copy(selectedPresets = settings.presets, selectedVolumes = settings.volumes)
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
    }
  }

  private fun selectPreset(channel: MidiChannel, preset: Preset) {
    midiEngine.setPreset(channel, preset)
    viewModelScope.launch { settingsRepository.setPreset(channel, preset) }
  }

  private fun commitVolume(channel: MidiChannel, value: Int) {
    midiEngine.setVolume(channel, value)
    viewModelScope.launch { settingsRepository.setVolume(channel, value) }
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
