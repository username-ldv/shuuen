package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

@Serializable
data class ChannelVolumes(
  val notes: Int = DefaultVolume.Notes.value,
  val drone: Int = DefaultVolume.Drone.value,
  val cadence: Int = DefaultVolume.Cadence.value,
) {
  fun forChannel(channel: MidiChannel): Int =
    when (channel) {
      MidiChannel.Notes -> notes
      MidiChannel.Drone -> drone
      MidiChannel.Cadence -> cadence
    }

  fun with(channel: MidiChannel, value: Int): ChannelVolumes =
    when (channel) {
      MidiChannel.Notes -> copy(notes = value)
      MidiChannel.Drone -> copy(drone = value)
      MidiChannel.Cadence -> copy(cadence = value)
    }
}
