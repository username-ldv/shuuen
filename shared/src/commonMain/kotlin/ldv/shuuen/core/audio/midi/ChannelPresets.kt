package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

@Serializable
data class ChannelPresets(
  val notes: Preset = DefaultPreset.Notes.preset,
  val drone: Preset = DefaultPreset.Drone.preset,
  val cadence: Preset = DefaultPreset.Cadence.preset,
) {
  fun forChannel(channel: MidiChannel): Preset =
    when (channel) {
      MidiChannel.Notes -> notes
      MidiChannel.Drone -> drone
      MidiChannel.Cadence -> cadence
    }
}