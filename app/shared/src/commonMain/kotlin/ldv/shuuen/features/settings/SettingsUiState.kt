package ldv.shuuen.features.settings

import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.ChannelVolumes
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetCutoffs
import ldv.shuuen.core.audio.midi.PresetCutoffScope
import ldv.shuuen.core.audio.midi.PresetVolumes
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.settings.DefaultLevelStatsWindow
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.PresetShuffleSettings
import ldv.shuuen.core.settings.ThemeSettings

/** A MIDI bank paired with the presets it contains. */
data class Soundbank(
  val bank: Int,
  val presets: List<Preset>,
) {
  val label: String get() = soundbankLabel(bank)
}

data class SettingsUiState(
  /** Persisted override; blank means to use [defaultBackendUrl]. */
  val backendUrl: String = "",
  val defaultBackendUrl: String = "",
  val backendStatus: BackendStatus = BackendStatus.Checking,
  val backendUrlDialogOpen: Boolean = false,
  val backendUrlDraft: String = "",
  val backendUrlError: String? = null,
  val loadingPresets: Boolean = true,
  val audioReady: Boolean = false,
  val errorMessage: String? = null,
  val soundbanks: List<Soundbank> = emptyList(),
  val selectedPresets: ChannelPresets = ChannelPresets(),
  val presetShuffle: PresetShuffleSettings = PresetShuffleSettings(),
  val selectedVolumes: ChannelVolumes = ChannelVolumes(),
  val presetVolumes: PresetVolumes = PresetVolumes(),
  val presetCutoffs: PresetCutoffs = PresetCutoffs(),
  val melodyOriginalVolumeBoost: Int = 0,
  /** Volume (0..127) of a melody level's backing track. */
  val backingTrackVolume: Int = 100,
  /** A backing track silences the MIDI melody so only the audio sounds. */
  val backingTrackMutesMelody: Boolean = false,
  val inputMethod: InputMethod = InputMethod(),
  val theme: ThemeSettings = ThemeSettings(),
  /** Names of the connected hardware MIDI keyboards; empty when none. */
  val midiKeyboardDevices: List<String> = emptyList(),
  /** MIDI keyboard answers must match the asked note's exact octave (off = octave independent). */
  val midiRespectOctaves: Boolean = false,
  val allowSevenAccidentalKeys: Boolean = false,
  val levelStatsWindow: Int = DefaultLevelStatsWindow,
  val musicLabels: MusicLabelSettings = MusicLabelSettings(),
  /** The category whose picker sheet is currently open, or null when closed. */
  val openPickerChannel: MidiChannel? = null,
  /**
   * The preset the open sheet last auditioned. It is what the channel is actually sounding, so
   * the channel's volume slider scales by this one's trim until the sheet closes.
   */
  val auditioningPreset: Preset? = null,
  /** The channel whose change-instrument sheet is open, or null when closed. */
  val openShuffleChannel: MidiChannel? = null,
  val openLabelEditor: LabelEditor? = null,
) {
  val effectiveBackendUrl: String
    get() = backendUrl.ifBlank { defaultBackendUrl }

  /** The preset [channel] is currently sounding: an audition in the open sheet, else its base. */
  fun activePreset(channel: MidiChannel): Preset =
    auditioningPreset?.takeIf { openPickerChannel == channel } ?: selectedPresets.forChannel(channel)
  /**
   * Persisted presets carry no name (only bank/id), so resolve against the
   * loaded soundbanks to show the real instrument name where possible.
   */
  fun resolvePreset(preset: Preset): Preset =
    soundbanks.firstOrNull { it.bank == preset.bank }
      ?.presets?.firstOrNull { it.id == preset.id }
      ?: preset

  /** Every preset chosen for [channel], named where the soundbanks know them. */
  fun resolvedChoices(channel: MidiChannel): List<Preset> =
    selectedPresets.choicesFor(channel).map(::resolvePreset)
}

enum class LabelEditor {
  Notes,
  Degrees,
}

sealed interface SettingsAction {
  data object OpenBackendUrlDialog : SettingsAction

  data object CloseBackendUrlDialog : SettingsAction

  data class SetBackendUrlDraft(val value: String) : SettingsAction

  data object SaveBackendUrl : SettingsAction

  data class SelectInputMethod(val inputMethod: InputMethod) : SettingsAction

  data class SetTheme(val theme: ThemeSettings) : SettingsAction

  data class SetMidiRespectOctaves(val value: Boolean) : SettingsAction

  data class SetAllowSevenAccidentalKeys(val value: Boolean) : SettingsAction

  data class SetLevelStatsWindow(val value: Int) : SettingsAction

  data class CommitLevelStatsWindow(val value: Int) : SettingsAction

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

  /** Adds [preset] to the channel's choices, or drops it; dropping the last one is refused. */
  data class TogglePreset(val channel: MidiChannel, val preset: Preset) : SettingsAction

  data class OpenShuffleModePicker(val channel: MidiChannel) : SettingsAction
  data object CloseShuffleModePicker : SettingsAction

  data class SetPresetShuffleMode(
    val channel: MidiChannel,
    val mode: PresetShuffleMode,
  ) : SettingsAction

  data class SetPerNoteShuffleOnImportedMelodies(val value: Boolean) : SettingsAction

  data class Preview(val channel: MidiChannel) : SettingsAction

  /** Auditions one row of the picker: the channel switches to [preset] until the sheet closes. */
  data class PreviewPreset(val channel: MidiChannel, val preset: Preset) : SettingsAction

  /** Applied live to the engine while dragging a preset's trim, not persisted. */
  data class SetPresetVolume(
    val channel: MidiChannel,
    val preset: Preset,
    val percent: Int,
  ) : SettingsAction

  data class CommitPresetVolume(val preset: Preset, val percent: Int) : SettingsAction

  /** Applied live to the engine while dragging a preset's brightness compensation. */
  data class SetPresetCutoff(
    val channel: MidiChannel,
    val preset: Preset,
    val cutoff: Int,
  ) : SettingsAction

  data class CommitPresetCutoff(val preset: Preset, val cutoff: Int) : SettingsAction

  data class SetPresetCutoffScope(
    val channel: MidiChannel,
    val preset: Preset,
    val scope: PresetCutoffScope,
  ) : SettingsAction

  /** Applied live to the engine while dragging, not persisted. */
  data class SetVolume(val channel: MidiChannel, val value: Int) : SettingsAction

  /** Persisted once the user releases the slider. */
  data class CommitVolume(val channel: MidiChannel, val value: Int) : SettingsAction

  /** Local slider state while dragging; persisted on commit. */
  data class SetMelodyOriginalVolumeBoost(val value: Int) : SettingsAction

  data class CommitMelodyOriginalVolumeBoost(val value: Int) : SettingsAction

  /** Local slider state while dragging; persisted on commit. */
  data class SetBackingTrackVolume(val value: Int) : SettingsAction

  data class CommitBackingTrackVolume(val value: Int) : SettingsAction

  data class SetBackingTrackMutesMelody(val value: Boolean) : SettingsAction
}

/** Bank 0 is the General MIDI set by convention; other banks are shown by number. */
fun soundbankLabel(bank: Int): String = if (bank == 0) "General MIDI" else "Bank $bank"

/** Raw MIDI program number, zero-padded for stable column width (e.g. "024"). */
fun presetNumber(preset: Preset): String = preset.id.toString().padStart(3, '0')

fun presetName(preset: Preset): String =
  preset.name?.takeIf { it.isNotBlank() } ?: "Preset ${preset.id}"

/** Preset column for a channel: its base instrument, plus how many alternatives follow it. */
fun presetChoicesLabel(choices: List<Preset>): String {
  val base = presetName(choices.first())
  return if (choices.size > 1) "$base +${choices.size - 1}" else base
}

/** Soundbank column for a channel; choices spread over several banks collapse to one label. */
fun soundbankChoicesLabel(choices: List<Preset>): String =
  if (choices.distinctBy { it.bank }.size > 1) "Mixed" else soundbankLabel(choices.first().bank)
