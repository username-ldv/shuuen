package ldv.shuuen.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.ChannelVolumes
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset

interface SettingsRepository {
  val settings: Flow<AppSettings>

  suspend fun setSoundFontPath(path: String?)

  suspend fun setPreset(channel: MidiChannel, preset: Preset)

  suspend fun setVolume(channel: MidiChannel, value: Int)

  suspend fun setMelodyOriginalVolumeBoost(value: Int)

  suspend fun setInputMethod(inputMethod: InputMethod)

  suspend fun setAllowSevenAccidentalKeys(value: Boolean)

  suspend fun setNoteNames(names: List<String>)

  suspend fun setDegreeNames(names: List<String>)

  suspend fun setCustomNoteNamesPreset(names: List<String>)

  suspend fun setCustomDegreeNamesPreset(names: List<String>)
}

@Serializable
data class MusicLabelSettings(
  val noteNames: List<String> = emptyList(),
  val degreeNames: List<String> = emptyList(),
  val customNoteNamesPreset: List<String> = emptyList(),
  val customDegreeNamesPreset: List<String> = emptyList(),
)

@Serializable
data class AppSettings(
  val soundFontPath: String? = null,
  val presets: ChannelPresets = ChannelPresets(),
  val volumes: ChannelVolumes = ChannelVolumes(),
  @SerialName("melodyOriginalVelocityBoost")
  val melodyOriginalVolumeBoost: Int = 0,
  val inputMethod: InputMethod = InputMethod(),
  val musicLabels: MusicLabelSettings = MusicLabelSettings(),
  /**
   * When true, the 7-sharp/7-flat key spellings (C♯, C♭ and their minor relatives) may be chosen
   * for the otherwise-ambiguous keys; when false they are excluded, so those keys resolve to their
   * 5-accidental enharmonic.
   */
  val allowSevenAccidentalKeys: Boolean = false,
)
