package ldv.shuuen.bass

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertTrue

class BassJvmSmokeTest {
  @Test
  fun createsAndLinksFileStreamsFromMemory() {
    Bass.load()
    // Device 0 is BASS's "no sound" device: always available, no audio hardware needed.
    // A false return with an error is fine when another test already initialized it.
    Bass.init(device = 0)

    val wav = monoWavBytes()
    val first = Bass.createFileStreamFromMemory(wav, Bass.BASS_STREAM_PRESCAN)
    val second = Bass.createFileStreamFromMemory(wav)
    try {
      assertTrue(first != 0, "first stream failed: error ${Bass.errorCode()}")
      assertTrue(second != 0, "second stream failed: error ${Bass.errorCode()}")
      assertTrue(Bass.channelGetLength(first) > 0)
      assertTrue(Bass.linkChannels(first, second), "link failed: error ${Bass.errorCode()}")
      assertTrue(Bass.unlinkChannels(first, second), "unlink failed: error ${Bass.errorCode()}")
    } finally {
      if (first != 0) Bass.freeStream(first)
      if (second != 0) Bass.freeStream(second)
    }
  }

  /** A tenth of a second of 16-bit mono silence as an in-memory RIFF/WAV file. */
  private fun monoWavBytes(sampleRate: Int = 44_100): ByteArray {
    val data = ByteArray(sampleRate / 10 * 2)
    val out = ByteArrayOutputStream()
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray())
    header.putInt(36 + data.size)
    header.put("WAVE".toByteArray())
    header.put("fmt ".toByteArray())
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(1) // mono
    header.putInt(sampleRate)
    header.putInt(sampleRate * 2)
    header.putShort(2)
    header.putShort(16)
    header.put("data".toByteArray())
    header.putInt(data.size)
    out.write(header.array())
    out.write(data)
    return out.toByteArray()
  }

  @Test
  fun loadsBassAndBassMidi() {
    Bass.load()

    assertTrue(Bass.version() != 0)
    assertTrue(Bass.midiVersion() != 0)
  }

  @Test
  fun enumeratesMidiInputDevicesWithoutCrashing() {
    Bass.load()

    assertTrue(Bass.midiInputSupported)
    // No keyboard may be attached; the binding just has to walk the list cleanly to its end.
    var device = 0
    while (true) {
      val info = Bass.midiInGetDeviceInfo(device) ?: break
      assertTrue(info.name.isNotEmpty())
      device++
    }
  }
}
