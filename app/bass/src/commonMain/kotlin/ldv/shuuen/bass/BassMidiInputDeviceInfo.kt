package ldv.shuuen.bass

/**
 * A MIDI input device as enumerated by BASSMIDI (BASS_MIDI_InGetDeviceInfo).
 *
 * [device] is the positional device number used by the other In* functions; it can shift when
 * devices are plugged or unplugged, so a change in the enumerated list invalidates old numbers.
 */
data class BassMidiInputDeviceInfo(
  val device: Int,
  val name: String,
  /** The device can be initialized only while this is true. */
  val enabled: Boolean,
  /** BASS_MIDI_InInit has been called on the device (by anyone in this process). */
  val initialized: Boolean,
)
