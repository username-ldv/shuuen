package ldv.shuuen.bass

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal actual object BassPlatform {
  private val libraries: NativeLibraries by lazy { NativeLibraries.load() }
  private val melodyFilters = mutableMapOf<Int, MidiFilterProc>()

  actual fun load() {
    libraries
  }

  actual fun version(): Int = libraries.bass.BASS_GetVersion()

  actual fun midiVersion(): Int = libraries.midi.BASS_MIDI_GetVersion()

  actual fun init(device: Int, frequency: Int, flags: Int): Boolean =
    libraries.bass.BASS_Init(device, frequency, flags, null, null)

  actual fun setConfig(option: Int, value: Int): Boolean =
    libraries.bass.BASS_SetConfig(option, value)

  actual fun free(): Boolean = libraries.bass.BASS_Free()

  actual fun freePlugins(handle: Int): Boolean = libraries.bass.BASS_PluginFree(handle)

  actual fun errorCode(): Int = libraries.bass.BASS_ErrorGetCode()

  actual fun createLiveMidiStream(channels: Int, flags: Int, frequency: Int): Int =
    libraries.midi.BASS_MIDI_StreamCreate(channels, flags, frequency)

  actual fun createMidiStream(filePath: String, flags: Int, frequency: Int): Int =
    libraries.midi.BASS_MIDI_StreamCreateFile(0, filePath, 0, 0, flags, frequency)

  actual fun createMidiStreamFromMemory(data: ByteArray, flags: Int, frequency: Int): Int {
    // BASSMIDI loads the whole MIDI at creation, so this memory is only needed during the call.
    val memory = Memory(data.size.toLong())
    memory.write(0, data, 0, data.size)
    return libraries.midi.BASS_MIDI_StreamCreateFile(
      BassConstants.BASS_FILE_MEM, memory, 0, data.size.toLong(), flags, frequency,
    )
  }

  actual fun streamGetEvents(streamHandle: Int, track: Int, filter: Int): List<BassMidiEvent> {
    val count = libraries.midi.BASS_MIDI_StreamGetEvents(streamHandle, track, filter, null)
    if (count <= 0) return emptyList()
    val eventSize = 5 * 4L // BASS_MIDI_EVENT = 5 DWORDs: event, param, chan, tick, pos
    val buffer = Memory(count * eventSize)
    libraries.midi.BASS_MIDI_StreamGetEvents(streamHandle, track, filter, buffer)
    return (0 until count).map { i ->
      val base = i * eventSize
      BassMidiEvent(
        event = buffer.getInt(base),
        param = buffer.getInt(base + 4),
        channel = buffer.getInt(base + 8),
        tick = buffer.getInt(base + 12).toUnsignedLong(),
        pos = buffer.getInt(base + 16).toUnsignedLong(),
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
      melodyFilters.remove(streamHandle)
      return libraries.midi.BASS_MIDI_StreamSetFilter(streamHandle, true, null, null)
    }

    val filter =
      MidiFilterProc { _, _, event, _, _ ->
        event.read()
        when (event.event) {
          BassConstants.MIDI_EVENT_NOTE -> {
            val velocity = (event.param shr 8) and 0xFF
            if (normalizeNoteVelocity && velocity > 0) {
              event.param = (event.param and 0xFF) or (NormalizedMidiValue shl 8)
              event.write()
            }
          }

          BassConstants.MIDI_EVENT_VOLUME,
          BassConstants.MIDI_EVENT_EXPRESSION -> {
            event.param = NormalizedMidiValue
            event.write()
          }

          BassConstants.MIDI_EVENT_PROGRAM,
          BassConstants.MIDI_EVENT_BANK,
          BassConstants.MIDI_EVENT_BANK_LSB,
          BassConstants.MIDI_EVENT_SYSTEM,
          BassConstants.MIDI_EVENT_SYSTEMEX -> return@MidiFilterProc false
        }
        true
      }

    melodyFilters[streamHandle] = filter
    return libraries.midi.BASS_MIDI_StreamSetFilter(streamHandle, true, filter, null)
  }

  actual fun loadSoundFont(filePath: String, flags: Int): Int =
    libraries.midi.BASS_MIDI_FontInit(filePath, flags)

  actual fun setStreamSoundFont(
    streamHandle: Int,
    soundFontHandle: Int,
    preset: Int,
    bank: Int,
  ): Boolean {
    val font = Memory(12).apply {
      setInt(0, soundFontHandle)
      setInt(4, preset)
      setInt(8, bank)
    }
    return libraries.midi.BASS_MIDI_StreamSetFonts(streamHandle, font, 1)
  }

  actual fun play(channelHandle: Int, restart: Boolean): Boolean =
    libraries.bass.BASS_ChannelPlay(channelHandle, restart)

  actual fun start(channelHandle: Int): Boolean =
    libraries.bass.BASS_ChannelStart(channelHandle)

  actual fun pause(channelHandle: Int): Boolean =
    libraries.bass.BASS_ChannelPause(channelHandle)

  actual fun stop(channelHandle: Int): Boolean =
    libraries.bass.BASS_ChannelStop(channelHandle)

  actual fun channelIsActive(channelHandle: Int): Int =
    libraries.bass.BASS_ChannelIsActive(channelHandle)

  actual fun channelGetPosition(channelHandle: Int, mode: Int): Long =
    libraries.bass.BASS_ChannelGetPosition(channelHandle, mode)

  actual fun channelSetPosition(channelHandle: Int, position: Long, mode: Int): Boolean =
    libraries.bass.BASS_ChannelSetPosition(channelHandle, position, mode)

  actual fun channelUpdate(channelHandle: Int, length: Int): Boolean =
    libraries.bass.BASS_ChannelUpdate(channelHandle, length)

  actual fun channelGetLength(channelHandle: Int, mode: Int): Long =
    libraries.bass.BASS_ChannelGetLength(channelHandle, mode)

  actual fun channelBytes2Seconds(channelHandle: Int, position: Long): Double =
    libraries.bass.BASS_ChannelBytes2Seconds(channelHandle, position)

  actual fun channelSeconds2Bytes(channelHandle: Int, seconds: Double): Long =
    libraries.bass.BASS_ChannelSeconds2Bytes(channelHandle, seconds)

  actual fun setChannelAttribute(channelHandle: Int, attribute: Int, value: Float): Boolean =
    libraries.bass.BASS_ChannelSetAttribute(channelHandle, attribute, value)

  actual fun streamEvent(streamHandle: Int, channel: Int, event: Int, parameter: Int): Boolean =
    libraries.midi.BASS_MIDI_StreamEvent(streamHandle, channel, event, parameter)

  actual fun getSoundFontPresets(soundFontHandle: Int): List<Int> {
    val info = BassMidiFontInfo()
    if (!libraries.midi.BASS_MIDI_FontGetInfo(soundFontHandle, info)) return emptyList()
    info.read()

    val packedPresets = IntArray(info.presets)
    if (!libraries.midi.BASS_MIDI_FontGetPresets(soundFontHandle, packedPresets)) return emptyList()
    return packedPresets.toList()
  }

  actual fun getSoundFontPresetName(soundFontHandle: Int, preset: Int, bank: Int): String? =
    libraries.midi.BASS_MIDI_FontGetPreset(soundFontHandle, preset, bank)

  actual fun freeStream(streamHandle: Int): Boolean {
    val freed = libraries.bass.BASS_StreamFree(streamHandle)
    melodyFilters.remove(streamHandle)
    return freed
  }

  actual fun freeSoundFont(soundFontHandle: Int): Boolean =
    libraries.midi.BASS_MIDI_FontFree(soundFontHandle)

  actual val midiInputSupported: Boolean = true

  actual fun midiInGetDeviceInfo(device: Int): BassMidiInputDeviceInfo? {
    val info = BassMidiDeviceInfo()
    if (!libraries.midi.BASS_MIDI_InGetDeviceInfo(device, info)) return null
    info.read()
    return BassMidiInputDeviceInfo(
      device = device,
      name = info.name?.getString(0) ?: "MIDI device $device",
      enabled = info.flags and BassDeviceEnabled != 0,
      initialized = info.flags and BassDeviceInit != 0,
    )
  }

  actual fun midiInInit(device: Int, onData: (ByteArray) -> Unit): Boolean {
    val proc = MidiInProc { _, _, buffer, length, _ ->
      if (length > 0) onData(buffer.getByteArray(0, length))
    }
    // The callback must stay strongly referenced while the device is open, or the JVM could
    // collect it under BASS's feet.
    midiInCallbacks[device] = proc
    val initialized = libraries.midi.BASS_MIDI_InInit(device, proc, null)
    if (!initialized) midiInCallbacks.remove(device)
    return initialized
  }

  actual fun midiInStart(device: Int): Boolean = libraries.midi.BASS_MIDI_InStart(device)

  actual fun midiInStop(device: Int): Boolean = libraries.midi.BASS_MIDI_InStop(device)

  actual fun midiInFree(device: Int): Boolean {
    val freed = libraries.midi.BASS_MIDI_InFree(device)
    if (freed) midiInCallbacks.remove(device)
    return freed
  }

  private val midiInCallbacks = mutableMapOf<Int, MidiInProc>()
}

private data class NativeLibraries(
  val bass: BassNative,
  val midi: BassMidiNative,
) {
  companion object {
    fun load(): NativeLibraries {
      val os = currentOs()
      val arch = currentArch()
      val extension = if (os == "windows") "dll" else "so"
      val prefix = if (os == "windows") "" else "lib"
      val bassPath = extractLibrary("/native/$os/$arch/${prefix}bass.$extension")
      val midiPath = extractLibrary("/native/$os/$arch/${prefix}bassmidi.$extension")
      val options = if (os == "windows") {
        mapOf(Library.OPTION_CALLING_CONVENTION to Function.ALT_CONVENTION)
      } else {
        emptyMap<String, Any>()
      }

      // Load BASS before BASSMIDI because the add-on depends on the core library.
      val bass = Native.load(bassPath.toString(), BassNative::class.java, options)
      val midi = Native.load(midiPath.toString(), BassMidiNative::class.java, options)
      return NativeLibraries(bass, midi)
    }
  }
}

private interface BassNative : Library {
  fun BASS_GetVersion(): Int
  fun BASS_ErrorGetCode(): Int
  fun BASS_SetConfig(option: Int, value: Int): Boolean
  fun BASS_Init(device: Int, frequency: Int, flags: Int, win: Pointer?, dsguid: Pointer?): Boolean
  fun BASS_Free(): Boolean
  fun BASS_PluginFree(handle: Int): Boolean
  fun BASS_ChannelPlay(handle: Int, restart: Boolean): Boolean
  fun BASS_ChannelStart(handle: Int): Boolean
  fun BASS_ChannelPause(handle: Int): Boolean
  fun BASS_ChannelStop(handle: Int): Boolean
  fun BASS_ChannelIsActive(handle: Int): Int
  fun BASS_ChannelGetPosition(handle: Int, mode: Int): Long
  fun BASS_ChannelSetPosition(handle: Int, pos: Long, mode: Int): Boolean
  fun BASS_ChannelUpdate(handle: Int, length: Int): Boolean
  fun BASS_ChannelGetLength(handle: Int, mode: Int): Long
  fun BASS_ChannelBytes2Seconds(handle: Int, pos: Long): Double
  fun BASS_ChannelSeconds2Bytes(handle: Int, pos: Double): Long
  fun BASS_ChannelSetAttribute(handle: Int, attrib: Int, value: Float): Boolean
  fun BASS_StreamFree(handle: Int): Boolean
}

private interface BassMidiNative : Library {
  fun BASS_MIDI_GetVersion(): Int
  fun BASS_MIDI_StreamCreate(channels: Int, flags: Int, frequency: Int): Int
  fun BASS_MIDI_StreamCreateFile(
    filetype: Int,
    file: String,
    offset: Long,
    length: Long,
    flags: Int,
    frequency: Int,
  ): Int

  fun BASS_MIDI_StreamCreateFile(
    mem: Int,
    file: Pointer,
    offset: Long,
    length: Long,
    flags: Int,
    frequency: Int,
  ): Int

  fun BASS_MIDI_StreamGetEvents(handle: Int, track: Int, filter: Int, events: Pointer?): Int
  fun BASS_MIDI_StreamSetFilter(
    handle: Int,
    seeking: Boolean,
    proc: MidiFilterProc?,
    user: Pointer?,
  ): Boolean

  fun BASS_MIDI_FontInit(file: String, flags: Int): Int
  fun BASS_MIDI_FontFree(handle: Int): Boolean
  fun BASS_MIDI_InGetDeviceInfo(device: Int, info: BassMidiDeviceInfo): Boolean
  fun BASS_MIDI_InInit(device: Int, proc: MidiInProc?, user: Pointer?): Boolean
  fun BASS_MIDI_InStart(device: Int): Boolean
  fun BASS_MIDI_InStop(device: Int): Boolean
  fun BASS_MIDI_InFree(device: Int): Boolean
  fun BASS_MIDI_StreamSetFonts(handle: Int, fonts: Pointer, count: Int): Boolean
  fun BASS_MIDI_StreamEvent(handle: Int, chan: Int, event: Int, param: Int): Boolean
  fun BASS_MIDI_FontGetInfo(handle: Int, info: BassMidiFontInfo): Boolean
  fun BASS_MIDI_FontGetPresets(handle: Int, presets: IntArray): Boolean
  fun BASS_MIDI_FontGetPreset(handle: Int, preset: Int, bank: Int): String?
}

class BassMidiFontInfo : Structure() {
  @JvmField
  var name: Pointer? = null

  @JvmField
  var copyright: Pointer? = null

  @JvmField
  var comment: Pointer? = null

  @JvmField
  var presets: Int = 0

  @JvmField
  var samsize: Int = 0

  @JvmField
  var samload: Int = 0

  @JvmField
  var samtype: Int = 0

  override fun getFieldOrder(): List<String> =
    listOf("name", "copyright", "comment", "presets", "samsize", "samload", "samtype")
}

open class BassMidiEventStruct : Structure() {
  @JvmField
  var event: Int = 0

  @JvmField
  var param: Int = 0

  @JvmField
  var chan: Int = 0

  @JvmField
  var tick: Int = 0

  @JvmField
  var pos: Int = 0

  override fun getFieldOrder(): List<String> = listOf("event", "param", "chan", "tick", "pos")

  class ByReference : BassMidiEventStruct(), Structure.ByReference
}

private fun interface MidiFilterProc : StdCallLibrary.StdCallCallback {
  fun invoke(
    handle: Int,
    track: Int,
    event: BassMidiEventStruct.ByReference,
    seeking: Boolean,
    user: Pointer?,
  ): Boolean
}

/** BASS_MIDI_DEVICEINFO: { char* name; DWORD id; DWORD flags } */
class BassMidiDeviceInfo : Structure() {
  @JvmField
  var name: Pointer? = null

  @JvmField
  var id: Int = 0

  @JvmField
  var flags: Int = 0

  override fun getFieldOrder(): List<String> = listOf("name", "id", "flags")
}

/** MIDIINPROC: void CALLBACK (DWORD device, double time, const BYTE* buffer, DWORD length, void* user) */
fun interface MidiInProc : StdCallLibrary.StdCallCallback {
  fun invoke(device: Int, time: Double, buffer: Pointer, length: Int, user: Pointer?)
}

// BASS_MIDI_DEVICEINFO status flags (BASS_DEVICE_* in bass.h).
private const val BassDeviceEnabled = 1
private const val BassDeviceInit = 4

private fun extractLibrary(resourcePath: String): Path {
  val target = libraryCachePath(resourcePath)
  if (target.exists()) return target

  val stream = BassPlatform::class.java.getResourceAsStream(resourcePath)
    ?: error("BASS native library resource not found: $resourcePath")

  Files.createDirectories(target.parent)
  stream.use { input ->
    target.outputStream().use { output ->
      input.copyTo(output)
    }
  }
  return target
}

private fun libraryCachePath(resourcePath: String): Path {
  val fileName = resourcePath.substringAfterLast('/')
  val directory = resourcePath.trim('/').substringBeforeLast('/').replace('/', '-')
  return Path.of(System.getProperty("java.io.tmpdir"), "shuuen-bass", directory, fileName)
}

private fun currentOs(): String {
  val os = System.getProperty("os.name").lowercase()
  return when {
    os.contains("win") -> "windows"
    os.contains("linux") -> "linux"
    else -> error("BASS desktop libraries are bundled only for Windows and Linux; current OS is $os")
  }
}

private fun currentArch(): String {
  val arch = System.getProperty("os.arch").lowercase()
  return when (arch) {
    "amd64", "x86_64" -> "x86_64"
    "x86", "i386", "i686" -> "x86"
    "aarch64", "arm64" -> "aarch64"
    "arm", "arm32" -> "armhf"
    else -> error("Unsupported BASS native architecture: $arch")
  }
}

private fun Int.toUnsignedLong(): Long = toLong() and 0xffffffffL

private const val NormalizedMidiValue = 127
