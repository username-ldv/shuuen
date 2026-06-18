package ldv.shuuen.domain.audio.midi

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
}
