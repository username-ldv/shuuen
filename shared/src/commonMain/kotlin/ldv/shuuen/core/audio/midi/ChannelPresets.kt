package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

/**
 * The instrument choice of each MIDI channel. A channel may hold several presets — a level then
 * picks among them on the schedule set by its [ldv.shuuen.core.settings.PresetShuffleMode].
 *
 * The single [notes]/[drone]/[cadence] fields stay the channel's base program: they mirror the
 * first entry of the matching choice list and are what plays outside a level (previews, free play)
 * and before a level's first roll. An empty choice list means "just that one preset", which is how
 * settings saved before multiple choices existed read back.
 */
@Serializable
data class ChannelPresets(
  val notes: Preset = DefaultPreset.Notes.preset,
  val drone: Preset = DefaultPreset.Drone.preset,
  val cadence: Preset = DefaultPreset.Cadence.preset,
  val notesChoices: List<Preset> = emptyList(),
  val droneChoices: List<Preset> = emptyList(),
  val cadenceChoices: List<Preset> = emptyList(),
) {
  fun forChannel(channel: MidiChannel): Preset =
    when (channel) {
      MidiChannel.Notes -> notes
      MidiChannel.Drone -> drone
      MidiChannel.Cadence -> cadence
    }

  /** True when some channel has more than one preset to pick from, i.e. anything to shuffle. */
  val hasChoices: Boolean
    get() = MidiChannel.entries.any { choicesFor(it).size > 1 }

  /** Every preset chosen for [channel], never empty. */
  fun choicesFor(channel: MidiChannel): List<Preset> =
    when (channel) {
      MidiChannel.Notes -> notesChoices
      MidiChannel.Drone -> droneChoices
      MidiChannel.Cadence -> cadenceChoices
    }.ifEmpty { listOf(forChannel(channel)) }

  /** Replaces [channel]'s choices, keeping its base preset on the list's first entry. */
  fun withChoices(channel: MidiChannel, choices: List<Preset>): ChannelPresets {
    val chosen = choices.distinctBy { it.toPacked() }.ifEmpty { return this }
    val base = chosen.first()
    return when (channel) {
      MidiChannel.Notes -> copy(notes = base, notesChoices = chosen)
      MidiChannel.Drone -> copy(drone = base, droneChoices = chosen)
      MidiChannel.Cadence -> copy(cadence = base, cadenceChoices = chosen)
    }
  }
}
