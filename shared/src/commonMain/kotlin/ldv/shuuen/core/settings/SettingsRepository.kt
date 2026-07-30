package ldv.shuuen.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.ChannelVolumes
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetVolumes

interface SettingsRepository {
  val settings: Flow<AppSettings>

  /** Overrides the platform backend URL; null restores the platform default. */
  suspend fun setBackendUrl(url: String?)

  suspend fun setSoundFontPath(path: String?)

  /** Replaces the presets chosen for [channel]; an empty list is ignored. */
  suspend fun setPresetChoices(channel: MidiChannel, presets: List<Preset>)

  suspend fun setPresetShuffleMode(channel: MidiChannel, mode: PresetShuffleMode)

  /** Trims [preset] to [percent] (0..100) of whatever channel volume it plays at. */
  suspend fun setPresetVolume(preset: Preset, percent: Int)

  suspend fun setPerNoteShuffleOnImportedMelodies(value: Boolean)

  suspend fun setVolume(channel: MidiChannel, value: Int)

  suspend fun setMelodyOriginalVolumeBoost(value: Int)

  suspend fun setBackingTrackVolume(value: Int)

  suspend fun setBackingTrackMutesMelody(value: Boolean)

  suspend fun setInputMethod(inputMethod: InputMethod)

  suspend fun setTheme(theme: ThemeSettings)

  suspend fun setMidiRespectOctaves(value: Boolean)

  suspend fun setAllowSevenAccidentalKeys(value: Boolean)

  suspend fun setLevelStatsWindow(value: Int)

  suspend fun setNoteNames(names: List<String>)

  suspend fun setDegreeNames(names: List<String>)

  suspend fun setCustomNoteNamesPreset(names: List<String>)

  suspend fun setCustomDegreeNamesPreset(names: List<String>)
}

const val DefaultLevelStatsWindow = 15
const val MinLevelStatsWindow = 1
const val MaxLevelStatsWindow = 100

fun coerceLevelStatsWindow(value: Int): Int =
  value.coerceIn(MinLevelStatsWindow, MaxLevelStatsWindow)

@Serializable
data class MusicLabelSettings(
  val noteNames: List<String> = emptyList(),
  val degreeNames: List<String> = emptyList(),
  val customNoteNamesPreset: List<String> = emptyList(),
  val customDegreeNamesPreset: List<String> = emptyList(),
)

@Serializable
data class AppSettings(
  /** Custom API base URL. Null keeps the platform/environment-provided default. */
  val backendUrl: String? = null,
  val soundFontPath: String? = null,
  val presets: ChannelPresets = ChannelPresets(),
  /** How each channel re-rolls among its chosen presets while a level runs. */
  val presetShuffle: PresetShuffleSettings = PresetShuffleSettings(),
  val volumes: ChannelVolumes = ChannelVolumes(),
  /** Per-instrument loudness trims, scaling whatever channel volume the preset plays at. */
  val presetVolumes: PresetVolumes = PresetVolumes(),
  @SerialName("melodyOriginalVelocityBoost")
  val melodyOriginalVolumeBoost: Int = 0,
  val inputMethod: InputMethod = InputMethod(),
  val theme: ThemeSettings = ThemeSettings(),
  /**
   * MIDI keyboard answers only: when true, a guess must land in the exact octave of the asked
   * note; when false (default) any octave of the right pitch class counts, matching the
   * octave-less on-screen inputs. On-screen taps are always octave independent.
   */
  val midiRespectOctaves: Boolean = false,
  val musicLabels: MusicLabelSettings = MusicLabelSettings(),
  /**
   * When true, the 7-sharp/7-flat key spellings (C♯, C♭ and their minor relatives) may be chosen
   * for the otherwise-ambiguous keys; when false they are excluded, so those keys resolve to their
   * 5-accidental enharmonic.
   */
  val allowSevenAccidentalKeys: Boolean = false,
  val levelStatsWindow: Int = DefaultLevelStatsWindow,
  /** Volume (0..127) of a melody level's backing track, applied when the level loads. */
  val backingTrackVolume: Int = 100,
  /**
   * When true, a melody level that has a backing track plays only that audio — the MIDI melody
   * stream is silenced (the quiz still follows the MIDI notes). When false both sound together.
   */
  val backingTrackMutesMelody: Boolean = false,
)
