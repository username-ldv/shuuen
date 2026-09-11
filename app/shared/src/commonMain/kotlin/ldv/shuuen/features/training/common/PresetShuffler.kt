package ldv.shuuen.features.training.common

import kotlin.random.Random
import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.PresetShuffleSettings

/**
 * Picks each channel's preset out of the set chosen in settings, on the schedule that channel's
 * [PresetShuffleMode] asks for. One instance covers one play session; the caller announces what
 * just happened ([atLevelStart], [atQuestion], [atMelodyNote], [manual]) and applies the returned
 * presets — only the channels that actually change are in the result.
 *
 * A channel with a single chosen preset never appears in any result after [atLevelStart]: there is
 * nothing to shuffle to. Rolls after the first always land on a *different* preset, so every change
 * is audible.
 *
 * [perNoteSupported] is true only where notes really do sound one at a time (a melody level, and
 * for an imported melody only when the setting allows it); elsewhere [PresetShuffleMode.PerNote]
 * degrades to [PresetShuffleMode.PerQuestion].
 */
class PresetShuffler(
  private val presets: ChannelPresets,
  private val modes: PresetShuffleSettings,
  private val perNoteSupported: Boolean,
  private val random: Random = Random.Default,
) {
  private val current = mutableMapOf<MidiChannel, Preset>()

  /** The level's opening preset for every channel, rolled fresh. */
  fun atLevelStart(): Map<MidiChannel, Preset> =
    MidiChannel.entries.associateWith { channel ->
      presets.choicesFor(channel).random(random).also { current[channel] = it }
    }

  fun atQuestion(): Map<MidiChannel, Preset> =
    rollAll { effectiveMode(it) == PresetShuffleMode.PerQuestion }

  fun atMelodyNote(): Map<MidiChannel, Preset> =
    rollAll { effectiveMode(it) == PresetShuffleMode.PerNote }

  /** The "shuffle now" button: every channel that has a choice re-rolls, whatever its mode. */
  fun manual(): Map<MidiChannel, Preset> = rollAll { true }

  private fun effectiveMode(channel: MidiChannel): PresetShuffleMode {
    val mode = modes.forChannel(channel)
    val perNoteApplies = perNoteSupported && channel == MidiChannel.Notes
    return if (mode == PresetShuffleMode.PerNote && !perNoteApplies) {
      PresetShuffleMode.PerQuestion
    } else {
      mode
    }
  }

  private fun rollAll(include: (MidiChannel) -> Boolean): Map<MidiChannel, Preset> =
    MidiChannel.entries
      .filter(include)
      .mapNotNull { channel -> rollDifferent(channel)?.let { channel to it } }
      .toMap()

  /** A random preset for [channel] other than the one sounding, or null when there is no other. */
  private fun rollDifferent(channel: MidiChannel): Preset? {
    val others =
      presets.choicesFor(channel).filter { it.toPacked() != current[channel]?.toPacked() }
    val next = others.randomOrNull(random) ?: return null
    current[channel] = next
    return next
  }
}
