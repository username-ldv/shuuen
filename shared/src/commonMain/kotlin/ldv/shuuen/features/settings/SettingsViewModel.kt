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
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.scaledChannelVolume
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.MusicLabelDefaults
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import kotlin.time.Duration.Companion.milliseconds

class SettingsViewModel(
    private val midiEngine: MidiEngine,
    private val settingsRepository: SettingsRepository,
    midiKeyboardInput: MidiKeyboardInput,
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
            presetShuffle = settings.presetShuffle,
            selectedVolumes = settings.volumes,
            presetVolumes = settings.presetVolumes,
            melodyOriginalVolumeBoost = settings.melodyOriginalVolumeBoost,
            backingTrackVolume = settings.backingTrackVolume,
            backingTrackMutesMelody = settings.backingTrackMutesMelody,
            inputMethod = settings.inputMethod,
            theme = settings.theme,
            midiRespectOctaves = settings.midiRespectOctaves,
            allowSevenAccidentalKeys = settings.allowSevenAccidentalKeys,
            levelStatsWindow = coerceLevelStatsWindow(settings.levelStatsWindow),
            musicLabels = settings.musicLabels,
          )
        }
      }
    }
    viewModelScope.launch {
      midiKeyboardInput.connectedDevices.collect { devices ->
        mutableState.update { it.copy(midiKeyboardDevices = devices) }
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

      is SettingsAction.SetTheme -> {
        viewModelScope.launch { settingsRepository.setTheme(action.theme) }
      }

      is SettingsAction.SetAllowSevenAccidentalKeys -> {
        viewModelScope.launch { settingsRepository.setAllowSevenAccidentalKeys(action.value) }
      }

      is SettingsAction.SetMidiRespectOctaves -> {
        viewModelScope.launch { settingsRepository.setMidiRespectOctaves(action.value) }
      }

      is SettingsAction.SetLevelStatsWindow -> {
        mutableState.update { it.copy(levelStatsWindow = coerceLevelStatsWindow(action.value)) }
      }

      is SettingsAction.CommitLevelStatsWindow -> {
        commitLevelStatsWindow(action.value)
      }

      is SettingsAction.OpenLabelEditor ->
        mutableState.update { it.copy(openLabelEditor = action.editor) }

      SettingsAction.CloseLabelEditor ->
        mutableState.update { it.copy(openLabelEditor = null) }

      is SettingsAction.SetNoteName -> setNoteName(action.index, action.value)

      is SettingsAction.SetDegreeName -> setDegreeName(action.index, action.value)

      is SettingsAction.SetNoteNames ->
        viewModelScope.launch { settingsRepository.setNoteNames(action.values.toSizedLabels(MusicLabelDefaults.NoteNames)) }

      is SettingsAction.SetDegreeNames ->
        viewModelScope.launch { settingsRepository.setDegreeNames(action.values.toSizedLabels(MusicLabelDefaults.DegreeNames)) }

      is SettingsAction.SaveCustomNoteNamesPreset ->
        viewModelScope.launch {
          settingsRepository.setCustomNoteNamesPreset(action.values.toSizedLabels(MusicLabelDefaults.NoteNames))
        }

      is SettingsAction.SaveCustomDegreeNamesPreset ->
        viewModelScope.launch {
          settingsRepository.setCustomDegreeNamesPreset(action.values.toSizedLabels(MusicLabelDefaults.DegreeNames))
        }

      is SettingsAction.OpenPicker ->
          mutableState.update { it.copy(openPickerChannel = action.channel) }

      SettingsAction.ClosePicker -> {
        previewJob?.cancel()
        midiEngine.stopAll()
        // Auditioning a row left the channel on it; outside the sheet it sounds its base preset.
        val channel = mutableState.value.openPickerChannel
        mutableState.update { it.copy(openPickerChannel = null, auditioningPreset = null) }
        channel?.let {
          midiEngine.setPreset(it, mutableState.value.selectedPresets.forChannel(it))
          applyChannelVolume(it)
        }
      }

      is SettingsAction.TogglePreset -> togglePreset(action.channel, action.preset)

      is SettingsAction.OpenShuffleModePicker ->
        mutableState.update { it.copy(openShuffleChannel = action.channel) }

      SettingsAction.CloseShuffleModePicker ->
        mutableState.update { it.copy(openShuffleChannel = null) }

      is SettingsAction.SetPresetShuffleMode -> {
        // Picking is the whole point of the sheet, so it closes behind the choice.
        mutableState.update { it.copy(openShuffleChannel = null) }
        viewModelScope.launch {
          settingsRepository.setPresetShuffleMode(action.channel, action.mode)
        }
      }

      is SettingsAction.SetPerNoteShuffleOnImportedMelodies ->
        viewModelScope.launch {
          settingsRepository.setPerNoteShuffleOnImportedMelodies(action.value)
        }

      is SettingsAction.Preview -> preview(action.channel)

      is SettingsAction.PreviewPreset -> {
        audition(action.channel, action.preset)
        preview(action.channel)
      }

      is SettingsAction.SetPresetVolume -> {
        // Dragging a trim auditions its preset, so a preview started on it follows the slider.
        audition(action.channel, action.preset)
        mutableState.update {
          it.copy(presetVolumes = it.presetVolumes.with(action.preset, action.percent))
        }
        applyChannelVolume(action.channel)
      }

      is SettingsAction.CommitPresetVolume ->
        viewModelScope.launch {
          settingsRepository.setPresetVolume(action.preset, action.percent)
        }

      is SettingsAction.SetVolume -> {
        mutableState.update { it.copy(selectedVolumes = it.selectedVolumes.with(action.channel, action.value)) }
        applyChannelVolume(action.channel)
      }

      is SettingsAction.CommitVolume -> commitVolume(action.channel, action.value)
      is SettingsAction.SetMelodyOriginalVolumeBoost ->
        mutableState.update { it.copy(melodyOriginalVolumeBoost = action.value.coerceIn(0, 127)) }

      is SettingsAction.CommitMelodyOriginalVolumeBoost ->
        commitMelodyOriginalVolumeBoost(action.value)

      is SettingsAction.SetBackingTrackVolume ->
        mutableState.update { it.copy(backingTrackVolume = action.value.coerceIn(0, 127)) }

      is SettingsAction.CommitBackingTrackVolume -> commitBackingTrackVolume(action.value)

      is SettingsAction.SetBackingTrackMutesMelody ->
        viewModelScope.launch { settingsRepository.setBackingTrackMutesMelody(action.value) }
    }
  }

  private fun commitBackingTrackVolume(value: Int) {
    val coerced = value.coerceIn(0, 127)
    mutableState.update { it.copy(backingTrackVolume = coerced) }
    viewModelScope.launch { settingsRepository.setBackingTrackVolume(coerced) }
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

  /**
   * Adds or removes [preset] from the channel's choices. The last remaining choice cannot be
   * removed — a channel always has an instrument. Choosing does not change what the channel is
   * sounding; each row's own preview button does that.
   */
  private fun togglePreset(channel: MidiChannel, preset: Preset) {
    val current = mutableState.value.selectedPresets.choicesFor(channel)
    val chosen = current.any { it.toPacked() == preset.toPacked() }
    val next =
      if (chosen) {
        current.filterNot { it.toPacked() == preset.toPacked() }.ifEmpty { return }
      } else {
        current + preset
      }
    viewModelScope.launch { settingsRepository.setPresetChoices(channel, next) }
  }

  /** Puts [preset] on the channel so the next preview (and any live trim) sounds it. */
  private fun audition(channel: MidiChannel, preset: Preset) {
    if (mutableState.value.auditioningPreset?.toPacked() != preset.toPacked()) {
      mutableState.update { it.copy(auditioningPreset = preset) }
      midiEngine.setPreset(channel, preset)
    }
    applyChannelVolume(channel)
  }

  /** Sends the channel volume the active preset's trim asks for. */
  private fun applyChannelVolume(channel: MidiChannel) {
    val state = mutableState.value
    midiEngine.setVolume(
      channel,
      scaledChannelVolume(
        state.selectedVolumes.forChannel(channel),
        state.presetVolumes.forPreset(state.activePreset(channel)),
      ),
    )
  }

  private fun commitVolume(channel: MidiChannel, value: Int) {
    mutableState.update { it.copy(selectedVolumes = it.selectedVolumes.with(channel, value)) }
    applyChannelVolume(channel)
    viewModelScope.launch { settingsRepository.setVolume(channel, value) }
  }

  private fun commitMelodyOriginalVolumeBoost(value: Int) {
    val coerced = value.coerceIn(0, 127)
    mutableState.update { it.copy(melodyOriginalVolumeBoost = coerced) }
    viewModelScope.launch { settingsRepository.setMelodyOriginalVolumeBoost(coerced) }
  }

  private fun commitLevelStatsWindow(value: Int) {
    val coerced = coerceLevelStatsWindow(value)
    mutableState.update { it.copy(levelStatsWindow = coerced) }
    viewModelScope.launch { settingsRepository.setLevelStatsWindow(coerced) }
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

private fun List<String>.toSizedLabels(defaults: List<String>): List<String> =
  List(defaults.size) { index -> getOrNull(index) ?: defaults[index] }
