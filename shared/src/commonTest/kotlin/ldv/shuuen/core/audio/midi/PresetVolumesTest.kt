package ldv.shuuen.core.audio.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetVolumesTest {
  private val piano = Preset(bank = 0, id = 0)
  private val violin = Preset(bank = 0, id = 40)
  private val otherBankPiano = Preset(bank = 1, id = 0)

  @Test
  fun `an untrimmed preset sounds at full volume`() {
    assertEquals(FullPresetVolume, PresetVolumes().forPreset(piano))
  }

  @Test
  fun `a trim belongs to one preset and leaves the others alone`() {
    val volumes = PresetVolumes().with(violin, 70)

    assertEquals(70, volumes.forPreset(violin))
    assertEquals(FullPresetVolume, volumes.forPreset(piano))
  }

  @Test
  fun `presets in different banks are trimmed separately`() {
    val volumes = PresetVolumes().with(piano, 60)

    assertEquals(60, volumes.forPreset(piano))
    assertEquals(FullPresetVolume, volumes.forPreset(otherBankPiano))
  }

  @Test
  fun `a preset put back to full is stored as no trim at all`() {
    val volumes = PresetVolumes().with(violin, 70).with(violin, FullPresetVolume)

    assertEquals(FullPresetVolume, volumes.forPreset(violin))
    assertTrue(volumes.percents.isEmpty(), "full volume is the default, not a stored value")
  }

  @Test
  fun `trims are clamped to the allowed range`() {
    assertEquals(0, PresetVolumes().with(violin, -20).forPreset(violin))
    assertEquals(FullPresetVolume, PresetVolumes().with(violin, 500).forPreset(violin))
  }

  @Test
  fun `the trim scales the channel volume rather than replacing it`() {
    assertEquals(100, scaledChannelVolume(channelVolume = 100, presetPercent = 100))
    assertEquals(70, scaledChannelVolume(channelVolume = 100, presetPercent = 70))
    // Half a channel that is already halved.
    assertEquals(31, scaledChannelVolume(channelVolume = 63, presetPercent = 50))
    assertEquals(0, scaledChannelVolume(channelVolume = 127, presetPercent = 0))
  }

  @Test
  fun `a scaled volume never leaves the MIDI range`() {
    assertEquals(MidiVolumeMax, scaledChannelVolume(channelVolume = 500, presetPercent = 100))
    assertEquals(0, scaledChannelVolume(channelVolume = -10, presetPercent = 100))
  }
}
