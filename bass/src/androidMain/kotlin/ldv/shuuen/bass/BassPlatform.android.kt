package ldv.shuuen.bass

import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
import java.nio.ByteBuffer

internal actual object BassPlatform {
  private val melodyFilters = mutableMapOf<Int, BASSMIDI.MIDIFILTERPROC>()

  actual fun load() {
    BASS.BASS_GetVersion()
    BASSMIDI.BASS_MIDI_GetVersion()
  }

  actual fun version(): Int = BASS.BASS_GetVersion()

  actual fun midiVersion(): Int = BASSMIDI.BASS_MIDI_GetVersion()

  actual fun init(device: Int, frequency: Int, flags: Int): Boolean =
    BASS.BASS_Init(device, frequency, flags)

  actual fun setConfig(option: Int, value: Int): Boolean =
    BASS.BASS_SetConfig(option, value)

  actual fun free(): Boolean = BASS.BASS_Free()

  actual fun freePlugins(handle: Int): Boolean = BASS.BASS_PluginFree(handle)

  actual fun errorCode(): Int = BASS.BASS_ErrorGetCode()

  actual fun createLiveMidiStream(channels: Int, flags: Int, frequency: Int): Int =
    BASSMIDI.BASS_MIDI_StreamCreate(channels, flags, frequency)

  actual fun createMidiStream(filePath: String, flags: Int, frequency: Int): Int =
    BASSMIDI.BASS_MIDI_StreamCreateFile(filePath, 0, 0, flags, frequency)

  actual fun createMidiStreamFromMemory(data: ByteArray, flags: Int, frequency: Int): Int {
    // A direct buffer is required so BASS can read the bytes; MIDI is fully loaded at creation.
    val buffer = ByteBuffer.allocateDirect(data.size).apply {
      put(data)
      position(0)
    }
    return BASSMIDI.BASS_MIDI_StreamCreateFile(buffer, 0, data.size.toLong(), flags, frequency)
  }

  actual fun streamGetEvents(streamHandle: Int, track: Int, filter: Int): List<BassMidiEvent> {
    val count = BASSMIDI.BASS_MIDI_StreamGetEvents(streamHandle, track, filter, null)
    if (count <= 0) return emptyList()
    val events = Array(count) { BASSMIDI.BASS_MIDI_EVENT() }
    BASSMIDI.BASS_MIDI_StreamGetEvents(streamHandle, track, filter, events)
    return events.map {
      BassMidiEvent(
        event = it.event,
        param = it.param,
        channel = it.chan,
        tick = it.tick.toUnsignedLong(),
        pos = it.pos.toUnsignedLong(),
      )
    }
  }

  actual fun setMidiStreamMelodyFilter(
    streamHandle: Int,
    enabled: Boolean,
    preset: Int,
    bank: Int,
    normalizeNoteVelocity: Boolean,
  ): Boolean {
    if (!enabled) {
      // The Android Java wrapper crashes in JNI when proc/user are null. The filter belongs to the
      // stream, so BASS_StreamFree clears it; keep the callback pinned until then.
      return true
    }

    val filter =
      object : BASSMIDI.MIDIFILTERPROC {
        override fun MIDIFILTERPROC(
          handle: Int,
          track: Int,
          event: BASSMIDI.BASS_MIDI_EVENT,
          seeking: Boolean,
          user: Any?,
        ): Boolean {
          when (event.event) {
            BASSMIDI.MIDI_EVENT_NOTE -> {
              val velocity = (event.param shr 8) and 0xFF
              if (normalizeNoteVelocity && velocity > 0) {
                event.param = (event.param and 0xFF) or (NormalizedMidiValue shl 8)
              }
            }

            BASSMIDI.MIDI_EVENT_VOLUME,
            BASSMIDI.MIDI_EVENT_EXPRESSION -> {
              event.param = NormalizedMidiValue
            }

            BASSMIDI.MIDI_EVENT_PROGRAM,
            BASSMIDI.MIDI_EVENT_BANK,
            BASSMIDI.MIDI_EVENT_BANK_LSB,
            BASSMIDI.MIDI_EVENT_SYSTEM,
            BASSMIDI.MIDI_EVENT_SYSTEMEX -> return false
          }
          return true
        }
      }

    melodyFilters[streamHandle] = filter
    return BASSMIDI.BASS_MIDI_StreamSetFilter(streamHandle, true, filter, MelodyFilterUser)
  }

  actual fun loadSoundFont(filePath: String, flags: Int): Int =
    BASSMIDI.BASS_MIDI_FontInit(filePath, flags)

  actual fun setStreamSoundFont(
    streamHandle: Int,
    soundFontHandle: Int,
    preset: Int,
    bank: Int,
  ): Boolean {
    val font = BASSMIDI.BASS_MIDI_FONT().apply {
      font = soundFontHandle
      this.preset = preset
      this.bank = bank
    }
    return BASSMIDI.BASS_MIDI_StreamSetFonts(streamHandle, arrayOf(font), 1)
  }

  actual fun play(channelHandle: Int, restart: Boolean): Boolean =
    BASS.BASS_ChannelPlay(channelHandle, restart)

  actual fun start(channelHandle: Int): Boolean =
    BASS.BASS_ChannelStart(channelHandle)

  actual fun pause(channelHandle: Int): Boolean =
    BASS.BASS_ChannelPause(channelHandle)

  actual fun stop(channelHandle: Int): Boolean =
    BASS.BASS_ChannelStop(channelHandle)

  actual fun channelIsActive(channelHandle: Int): Int =
    BASS.BASS_ChannelIsActive(channelHandle)

  actual fun channelGetPosition(channelHandle: Int, mode: Int): Long =
    BASS.BASS_ChannelGetPosition(channelHandle, mode)

  actual fun channelSetPosition(channelHandle: Int, position: Long, mode: Int): Boolean =
    BASS.BASS_ChannelSetPosition(channelHandle, position, mode)

  actual fun channelUpdate(channelHandle: Int, length: Int): Boolean =
    BASS.BASS_ChannelUpdate(channelHandle, length)

  actual fun channelGetLength(channelHandle: Int, mode: Int): Long =
    BASS.BASS_ChannelGetLength(channelHandle, mode)

  actual fun channelBytes2Seconds(channelHandle: Int, position: Long): Double =
    BASS.BASS_ChannelBytes2Seconds(channelHandle, position)

  actual fun channelSeconds2Bytes(channelHandle: Int, seconds: Double): Long =
    BASS.BASS_ChannelSeconds2Bytes(channelHandle, seconds)

  actual fun setChannelAttribute(channelHandle: Int, attribute: Int, value: Float): Boolean =
    BASS.BASS_ChannelSetAttribute(channelHandle, attribute, value)

  actual fun streamEvent(streamHandle: Int, channel: Int, event: Int, parameter: Int): Boolean =
    BASSMIDI.BASS_MIDI_StreamEvent(streamHandle, channel, event, parameter)

  actual fun getSoundFontPresets(soundFontHandle: Int): List<Int> {
    val info = BASSMIDI.BASS_MIDI_FONTINFO()
    if (!BASSMIDI.BASS_MIDI_FontGetInfo(soundFontHandle, info)) return emptyList()

    val packedPresets = IntArray(info.presets)
    if (!BASSMIDI.BASS_MIDI_FontGetPresets(soundFontHandle, packedPresets)) return emptyList()
    return packedPresets.toList()
  }

  actual fun getSoundFontPresetName(soundFontHandle: Int, preset: Int, bank: Int): String? =
    BASSMIDI.BASS_MIDI_FontGetPreset(soundFontHandle, preset, bank)

  actual fun freeStream(streamHandle: Int): Boolean {
    val freed = BASS.BASS_StreamFree(streamHandle)
    melodyFilters.remove(streamHandle)
    return freed
  }

  actual fun freeSoundFont(soundFontHandle: Int): Boolean =
    BASSMIDI.BASS_MIDI_FontFree(soundFontHandle)

  // BASSMIDI has no MIDI input on Android (per its docs); the app uses android.media.midi instead.
  actual val midiInputSupported: Boolean = false

  actual fun midiInGetDeviceInfo(device: Int): BassMidiInputDeviceInfo? = null

  actual fun midiInInit(device: Int, onData: (ByteArray) -> Unit): Boolean = false

  actual fun midiInStart(device: Int): Boolean = false

  actual fun midiInStop(device: Int): Boolean = false

  actual fun midiInFree(device: Int): Boolean = false
}

private fun Int.toUnsignedLong(): Long = toLong() and 0xffffffffL

private const val NormalizedMidiValue = 127

private object MelodyFilterUser
