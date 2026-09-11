package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

/** BASSMIDI's neutral MIDI_EVENT_CUTOFF / CC74 value; values above it brighten the preset. */
const val NeutralPresetCutoff = 64
const val MaximumPresetCutoff = 127

@Serializable
enum class PresetCutoffScope {
  /** Apply only to imported MIDI melodies when their original note velocities are preserved. */
  OriginalVelocityMelodies,

  /** Apply whenever the preset sounds, including live singles, chords, and previews. */
  AllPlayback,
}

val DefaultPresetCutoffScope = PresetCutoffScope.OriginalVelocityMelodies

/**
 * Optional velocity-filter compensation per instrument.
 *
 * Some SoundFont presets intentionally close their low-pass filter as note velocity falls. A
 * stored value raises MIDI CC74 for that preset; an absent value leaves its authored response
 * alone. The setting belongs to the preset rather than a channel, just like [PresetVolumes].
 */
@Serializable
data class PresetCutoffs(
  /** Packed (bank, id) to CC74 value. Absence means that no compensation is configured. */
  val values: Map<Int, Int> = emptyMap(),
  /** Packed (bank, id) to non-default application scope. */
  val scopes: Map<Int, PresetCutoffScope> = emptyMap(),
) {
  fun forPreset(preset: Preset): Int? =
    values[preset.toPacked()]
      ?.takeIf { it > NeutralPresetCutoff }
      ?.coerceAtMost(MaximumPresetCutoff)

  fun with(preset: Preset, cutoff: Int): PresetCutoffs {
    val packed = preset.toPacked()
    val next =
      if (cutoff <= NeutralPresetCutoff) values - packed
      else values + (packed to cutoff.coerceAtMost(MaximumPresetCutoff))
    return copy(values = next)
  }

  fun scopeForPreset(preset: Preset): PresetCutoffScope =
    scopes[preset.toPacked()] ?: DefaultPresetCutoffScope

  fun withScope(preset: Preset, scope: PresetCutoffScope): PresetCutoffs {
    val packed = preset.toPacked()
    val next =
      if (scope == DefaultPresetCutoffScope) scopes - packed else scopes + (packed to scope)
    return copy(scopes = next)
  }

  /** The configured cutoff when [preset]'s scope includes this playback context. */
  fun effectiveForPreset(preset: Preset, originalVelocityMelody: Boolean): Int? =
    forPreset(preset)?.takeIf {
      originalVelocityMelody || scopeForPreset(preset) == PresetCutoffScope.AllPlayback
    }
}
