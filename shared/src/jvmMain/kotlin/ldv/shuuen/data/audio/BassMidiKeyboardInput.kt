package ldv.shuuen.data.audio

import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ldv.shuuen.bass.Bass
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.audio.input.MidiMessageParser

/**
 * Desktop MIDI keyboard input on top of BASSMIDI's BASS_MIDI_In* API. Polls the device list for
 * plug/unplug (there is no native hot-plug callback) and forwards note events from every device
 * it can open.
 */
class BassMidiKeyboardInput : MidiKeyboardInput {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
  override val connectedDevices: StateFlow<List<String>> = _connectedDevices.asStateFlow()

  private val _events =
    MutableSharedFlow<MidiKeyboardEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  override val events: SharedFlow<MidiKeyboardEvent> = _events.asSharedFlow()

  /** Enumeration snapshot the open devices were built from, to detect plug/unplug between polls. */
  private var lastSnapshot: List<EnumeratedDevice> = emptyList()

  private val openDevices = mutableSetOf<Int>()

  private data class EnumeratedDevice(val device: Int, val name: String, val enabled: Boolean)

  init {
    scope.launch {
      // Device enumeration only needs the BASS libraries loaded, not an audio output device.
      val loaded = runCatching { Bass.load() }
      if (loaded.isFailure) {
        Napier.w(loaded.exceptionOrNull()) { "BASS unavailable; MIDI keyboard input disabled" }
        return@launch
      }
      if (!Bass.midiInputSupported) return@launch
      while (isActive) {
        runCatching { refreshDevices() }
          .onFailure { Napier.w(it) { "MIDI input device refresh failed" } }
        delay(PollInterval)
      }
    }
  }

  private fun refreshDevices() {
    val snapshot = enumerate()
    if (snapshot == lastSnapshot) return
    lastSnapshot = snapshot

    // Device numbers are positional, so any plug/unplug can renumber the survivors; rebuilding
    // every open device on any change keeps numbers and callbacks consistent.
    closeAll()
    val names = mutableListOf<String>()
    for (candidate in snapshot) {
      if (!candidate.enabled) continue
      if (openDevice(candidate.device)) {
        openDevices += candidate.device
        names += candidate.name
        Napier.v { "Opened MIDI input device ${candidate.device}: ${candidate.name}" }
      } else {
        // Likely claimed by another application; the next list change retries it.
        Napier.w { "Couldn't open MIDI input device ${candidate.name} (error ${Bass.errorCode()})" }
      }
    }
    _connectedDevices.value = names
  }

  private fun enumerate(): List<EnumeratedDevice> {
    val devices = mutableListOf<EnumeratedDevice>()
    var index = 0
    while (true) {
      val info = Bass.midiInGetDeviceInfo(index) ?: break
      devices += EnumeratedDevice(index, info.name, info.enabled)
      index++
    }
    return devices
  }

  private fun openDevice(device: Int): Boolean {
    val parser = MidiMessageParser()
    val initialized =
      Bass.midiInInit(device) { bytes ->
        // Called on BASS's MIDI thread: parse and hand off without blocking.
        parser.feed(bytes).forEach(_events::tryEmit)
      }
    if (!initialized) return false
    if (Bass.midiInStart(device)) return true
    Bass.midiInFree(device)
    return false
  }

  private fun closeAll() {
    openDevices.forEach { device ->
      Bass.midiInStop(device)
      Bass.midiInFree(device)
    }
    openDevices.clear()
  }

  private companion object {
    val PollInterval = 2.seconds
  }
}
