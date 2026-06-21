package ldv.shuuen.ui.screens.app_settings

import ldv.shuuen.domain.audio.midi.ChannelPresets
import ldv.shuuen.domain.audio.midi.ChannelVolumes
import ldv.shuuen.domain.audio.midi.MidiChannel
import ldv.shuuen.domain.audio.midi.Preset

/** A MIDI bank paired with the presets it contains. */
data class Soundbank(
  val bank: Int,
  val presets: List<Preset>,
) {
  val label: String get() = soundbankLabel(bank)
}

data class SettingsUiState(
  val loadingPresets: Boolean = true,
  val audioReady: Boolean = false,
  val errorMessage: String? = null,
  val soundbanks: List<Soundbank> = emptyList(),
  val selectedPresets: ChannelPresets = ChannelPresets(),
  val selectedVolumes: ChannelVolumes = ChannelVolumes(),
  val melodyOriginalVolumeBoost: Int = 0,
  /** The category whose picker sheet is currently open, or null when closed. */
  val openPickerChannel: MidiChannel? = null,
) {
  /**
   * Persisted presets carry no name (only bank/id), so resolve against the
   * loaded soundbanks to show the real instrument name where possible.
   */
  fun resolvePreset(preset: Preset): Preset =
    soundbanks.firstOrNull { it.bank == preset.bank }
      ?.presets?.firstOrNull { it.id == preset.id }
      ?: preset
}

sealed interface SettingsAction {
  data class OpenPicker(val channel: MidiChannel) : SettingsAction
  data object ClosePicker : SettingsAction
  data class SelectPreset(val channel: MidiChannel, val preset: Preset) : SettingsAction
  data class Preview(val channel: MidiChannel) : SettingsAction

  /** Applied live to the engine while dragging, not persisted. */
  data class SetVolume(val channel: MidiChannel, val value: Int) : SettingsAction

  /** Persisted once the user releases the slider. */
  data class CommitVolume(val channel: MidiChannel, val value: Int) : SettingsAction

  /** Local slider state while dragging; persisted on commit. */
  data class SetMelodyOriginalVolumeBoost(val value: Int) : SettingsAction

  data class CommitMelodyOriginalVolumeBoost(val value: Int) : SettingsAction
}

/** Bank 0 is the General MIDI set by convention; other banks are shown by number. */
fun soundbankLabel(bank: Int): String = if (bank == 0) "General MIDI" else "Bank $bank"

/** Raw MIDI program number, zero-padded for stable column width (e.g. "024"). */
fun presetNumber(preset: Preset): String = preset.id.toString().padStart(3, '0')

fun presetName(preset: Preset): String =
  preset.name?.takeIf { it.isNotBlank() } ?: "Preset ${preset.id}"
