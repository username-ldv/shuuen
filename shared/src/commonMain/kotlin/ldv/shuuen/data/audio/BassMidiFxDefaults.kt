package ldv.shuuen.data.audio

import ldv.shuuen.bass.Bass

internal object BassMidiFxDefaults {
  fun applyToStream(streamHandle: Int): Boolean {
    if (streamHandle == 0) return false
    return listOf(
      Bass.streamEvent(streamHandle, GlobalEventChannel, Bass.MIDI_EVENT_REVERB_LEVEL, ReverbLevel),
      Bass.streamEvent(streamHandle, GlobalEventChannel, Bass.MIDI_EVENT_CHORUS_MACRO, ChorusType),
      Bass.streamEvent(streamHandle, GlobalEventChannel, Bass.MIDI_EVENT_CHORUS_LEVEL, ChorusLevel),
    ).all { it }
  }

  private const val GlobalEventChannel = 0
  private const val ReverbLevel = 60
  private const val ChorusType = 3
  private const val ChorusLevel = 43
}
