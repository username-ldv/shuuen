package ldv.shuuen.data.audio

import io.github.aakira.napier.Napier
import ldv.shuuen.bass.Bass

/** Requests BASSMIDI's highest documented interpolation quality, without making audio startup fail. */
internal fun enableSincMidiInterpolation(streamHandle: Int) {
  if (!Bass.setChannelAttribute(streamHandle, Bass.BASS_ATTRIB_MIDI_SRC, Sinc16PointQuality)) {
    // Sinc is unavailable without SSE2/NEON. BASS keeps its default linear interpolation in that
    // case, which is preferable to making the application silent on an old device.
    Napier.w { "16-point BASSMIDI sinc interpolation is unavailable; using the default SRC." }
  }
}

private const val Sinc16PointQuality = 2f
