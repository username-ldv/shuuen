package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

/** A preset sounding at its full share of whatever channel volume is set. */
const val FullPresetVolume = 100

/**
 * A loudness trim per instrument, independent of everything else: soundfont presets are not
 * mastered to a common level, so a violin may need to sit at 70% where a piano sits at 100%.
 *
 * The trim belongs to the preset itself, not to a channel — the same instrument is trimmed the
 * same wherever it plays — and it survives whether that preset is currently chosen or not. It
 * scales the channel volume rather than replacing it: the channel slider stays the master.
 */
@Serializable
data class PresetVolumes(
  /** Packed (bank, id) → percent of the channel volume. Absent means [FullPresetVolume]. */
  val percents: Map<Int, Int> = emptyMap(),
) {
  fun forPreset(preset: Preset): Int =
    percents[preset.toPacked()]?.coerceIn(0, FullPresetVolume) ?: FullPresetVolume

  fun with(preset: Preset, percent: Int): PresetVolumes {
    val coerced = percent.coerceIn(0, FullPresetVolume)
    // Full volume is the default, so it is stored as an absence — the map only holds real trims.
    val next =
      if (coerced == FullPresetVolume) percents - preset.toPacked()
      else percents + (preset.toPacked() to coerced)
    return copy(percents = next)
  }
}

/** The MIDI channel volume that sounds a preset trimmed to [presetPercent] of [channelVolume]. */
fun scaledChannelVolume(channelVolume: Int, presetPercent: Int): Int =
  channelVolume.coerceIn(0, MidiVolumeMax) * presetPercent.coerceIn(0, FullPresetVolume) /
    FullPresetVolume

const val MidiVolumeMax = 127
