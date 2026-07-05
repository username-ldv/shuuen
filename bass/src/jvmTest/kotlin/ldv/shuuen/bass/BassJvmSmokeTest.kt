package ldv.shuuen.bass

import kotlin.test.Test
import kotlin.test.assertTrue

class BassJvmSmokeTest {
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
