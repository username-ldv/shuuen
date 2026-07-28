package ldv.shuuen.features.training.common

import kotlinx.coroutines.flow.first
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.ChannelVolumes
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetVolumes
import ldv.shuuen.core.audio.midi.scaledChannelVolume
import ldv.shuuen.core.settings.PresetShuffleSettings
import ldv.shuuen.core.settings.SettingsRepository

/**
 * Owns a play session's MIDI presets: rolls them through a [PresetShuffler] and routes each roll
 * to whatever is making the sound. The settings are read once, when the level starts — a level
 * keeps the instruments it began with.
 *
 * The engine is shared with the rest of the app, so a level's rolls must not outlive it: [restore]
 * puts the channels back on the presets chosen in settings and belongs in the ViewModel's
 * `onCleared`.
 */
class LevelPresetController(
  private val midiEngine: MidiEngine,
  private val settingsRepository: SettingsRepository,
) {
  /**
   * Where the Notes channel's preset goes. Imported melodies sound on the file player's own
   * stream rather than the live engine, so that screen redirects it — the file player applies the
   * instrument's own loudness trim itself.
   */
  var notesSink: (Preset) -> Unit = { sound(MidiChannel.Notes, it) }

  private var shuffler: PresetShuffler? = null

  /** The settings the level opened with; null until [begin], which is why [restore] can no-op. */
  private var chosen: ChannelPresets? = null
  private var volumes: ChannelVolumes = ChannelVolumes()
  private var presetVolumes: PresetVolumes = PresetVolumes()

  /** Starts a session whose notes never sound one at a time (singles, chords). */
  suspend fun begin() = begin { false }

  /**
   * Starts a session, applying each channel's opening preset. [perNoteSupported] answers, for the
   * loaded settings, whether this level really plays its melody note by note.
   */
  suspend fun begin(perNoteSupported: (PresetShuffleSettings) -> Boolean) {
    val settings = settingsRepository.settings.first()
    chosen = settings.presets
    volumes = settings.volumes
    presetVolumes = settings.presetVolumes
    val shuffler =
      PresetShuffler(
        presets = settings.presets,
        modes = settings.presetShuffle,
        perNoteSupported = perNoteSupported(settings.presetShuffle),
      )
    this.shuffler = shuffler
    apply(shuffler.atLevelStart())
  }

  fun onQuestion() = apply(shuffler?.atQuestion())

  fun onMelodyNote() = apply(shuffler?.atMelodyNote())

  /** The "shuffle now" button in the level's top bar. */
  fun shuffleNow() = apply(shuffler?.manual())

  /**
   * Hands the channels back to the presets chosen in settings. A level that never started (a file
   * that wouldn't load, say) rolled nothing and so has nothing to hand back.
   */
  fun restore() {
    val chosen = chosen ?: return
    shuffler = null
    apply(MidiChannel.entries.associateWith { chosen.forChannel(it) })
  }

  private fun apply(rolled: Map<MidiChannel, Preset>?) {
    rolled?.forEach { (channel, preset) ->
      if (channel == MidiChannel.Notes) notesSink(preset) else sound(channel, preset)
    }
  }

  /** Puts [preset] on [channel] at the channel volume its own loudness trim asks for. */
  private fun sound(channel: MidiChannel, preset: Preset) {
    midiEngine.setPreset(channel, preset)
    midiEngine.setVolume(
      channel,
      scaledChannelVolume(volumes.forChannel(channel), presetVolumes.forPreset(preset)),
    )
  }
}
