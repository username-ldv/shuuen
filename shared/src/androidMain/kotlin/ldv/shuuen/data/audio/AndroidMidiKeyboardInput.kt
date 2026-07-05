package ldv.shuuen.data.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.audio.input.MidiMessageParser

/**
 * Android MIDI keyboard input via android.media.midi (BASSMIDI has no MIDI input on Android).
 * Hot-plug comes from [MidiManager]'s device callback; a device's *output* ports are our input.
 * All device bookkeeping runs on the main-looper [handler], so no extra synchronization is needed.
 */
class AndroidMidiKeyboardInput(context: Context) : MidiKeyboardInput {
  private val _connectedDevices = MutableStateFlow<List<String>>(emptyList())
  override val connectedDevices: StateFlow<List<String>> = _connectedDevices.asStateFlow()

  private val _events =
    MutableSharedFlow<MidiKeyboardEvent>(
      extraBufferCapacity = 64,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  override val events: SharedFlow<MidiKeyboardEvent> = _events.asSharedFlow()

  private val handler = Handler(Looper.getMainLooper())
  private val openDevices = mutableMapOf<Int, OpenDevice>()

  private class OpenDevice(val name: String, val device: MidiDevice, val ports: List<MidiOutputPort>)

  init {
    val manager =
      if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
      } else {
        null
      }
    if (manager == null) {
      Napier.v { "MIDI feature unavailable on this device; MIDI keyboard input disabled" }
    } else {
      manager.registerDeviceCallback(
        object : MidiManager.DeviceCallback() {
          override fun onDeviceAdded(device: MidiDeviceInfo) = open(manager, device)

          override fun onDeviceRemoved(device: MidiDeviceInfo) = close(device.id)
        },
        handler,
      )
      // registerDeviceCallback doesn't replay devices that are already plugged in.
      @Suppress("DEPRECATION") manager.devices.forEach { open(manager, it) }
    }
  }

  private fun open(manager: MidiManager, info: MidiDeviceInfo) {
    if (info.outputPortCount == 0 || openDevices.containsKey(info.id)) return
    val name = displayName(info)
    manager.openDevice(
      info,
      { device ->
        when {
          device == null -> Napier.w { "Couldn't open MIDI input device $name" }

          // The device may have been unplugged (or opened twice) while the open was in flight.
          openDevices.containsKey(info.id) || info !in currentDeviceInfos(manager) ->
            device.close()

          else -> {
            val ports =
              (0 until info.outputPortCount).mapNotNull { portNumber ->
                device.openOutputPort(portNumber)?.also { port ->
                  // One parser per port: running status must not interleave across streams.
                  val parser = MidiMessageParser()
                  port.connect(
                    object : MidiReceiver() {
                      override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        parser.feed(msg, offset, count).forEach(_events::tryEmit)
                      }
                    }
                  )
                }
              }
            if (ports.isEmpty()) {
              Napier.w { "No MIDI output ports could be opened on $name" }
              device.close()
            } else {
              openDevices[info.id] = OpenDevice(name, device, ports)
              refreshNames()
              Napier.v { "Opened MIDI input device $name" }
            }
          }
        }
      },
      handler,
    )
  }

  private fun close(deviceId: Int) {
    val open = openDevices.remove(deviceId) ?: return
    runCatching {
      open.ports.forEach { it.close() }
      open.device.close()
    }
    refreshNames()
  }

  private fun refreshNames() {
    _connectedDevices.value = openDevices.values.map { it.name }
  }

  private fun currentDeviceInfos(manager: MidiManager): Array<MidiDeviceInfo> =
    @Suppress("DEPRECATION") manager.devices

  private fun displayName(info: MidiDeviceInfo): String =
    info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
      ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
      ?: "MIDI device ${info.id}"
}
