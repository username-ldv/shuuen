package ldv.shuuen.features.settings

import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.ChannelVolumes
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.MusicLabelSettings

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
  val inputMethod: InputMethod = InputMethod(),
  val allowSevenAccidentalKeys: Boolean = false,
  val musicLabels: MusicLabelSettings = MusicLabelSettings(),
  /** The category whose picker sheet is currently open, or null when closed. */
  val openPickerChannel: MidiChannel? = null,
  val openLabelEditor: LabelEditor? = null,
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

enum class LabelEditor {
  Notes,
  Degrees,
}

sealed interface SettingsAction {
  data class SelectInputMethod(val inputMethod: InputMethod) : SettingsAction

  data class SetAllowSevenAccidentalKeys(val value: Boolean) : SettingsAction

  data class OpenLabelEditor(val editor: LabelEditor) : SettingsAction
  data object CloseLabelEditor : SettingsAction
  data class SetNoteName(val index: Int, val value: String) : SettingsAction
  data class SetDegreeName(val index: Int, val value: String) : SettingsAction
  data class SetNoteNames(val values: List<String>) : SettingsAction
  data class SetDegreeNames(val values: List<String>) : SettingsAction
  data class SaveCustomNoteNamesPreset(val values: List<String>) : SettingsAction
  data class SaveCustomDegreeNamesPreset(val values: List<String>) : SettingsAction

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
