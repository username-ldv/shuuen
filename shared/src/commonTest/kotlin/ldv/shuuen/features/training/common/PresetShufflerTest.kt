package ldv.shuuen.features.training.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import ldv.shuuen.core.audio.midi.ChannelPresets
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.PresetShuffleSettings

class PresetShufflerTest {
  private fun preset(id: Int) = Preset(bank = 0, id = id)

  private fun presets(
    notes: List<Int> = listOf(0),
    drone: List<Int> = listOf(42),
    cadence: List<Int> = listOf(0),
  ) =
    ChannelPresets()
      .withChoices(MidiChannel.Notes, notes.map(::preset))
      .withChoices(MidiChannel.Drone, drone.map(::preset))
      .withChoices(MidiChannel.Cadence, cadence.map(::preset))

  private fun shuffler(
    presets: ChannelPresets,
    modes: PresetShuffleSettings,
    perNoteSupported: Boolean = false,
  ) = PresetShuffler(presets, modes, perNoteSupported, Random(1))

  @Test
  fun `level start rolls every channel out of its own choices`() {
    val shuffler =
      shuffler(
        presets(notes = listOf(1, 2, 3), drone = listOf(40, 41)),
        PresetShuffleSettings(),
      )

    val rolled = shuffler.atLevelStart()

    assertEquals(MidiChannel.entries.toSet(), rolled.keys)
    assertTrue(rolled.getValue(MidiChannel.Notes).id in listOf(1, 2, 3))
    assertTrue(rolled.getValue(MidiChannel.Drone).id in listOf(40, 41))
    assertEquals(0, rolled.getValue(MidiChannel.Cadence).id)
  }

  @Test
  fun `a per-level channel never rolls again`() {
    val shuffler =
      shuffler(presets(notes = listOf(1, 2)), PresetShuffleSettings(notes = PresetShuffleMode.PerLevel))
    shuffler.atLevelStart()

    assertEquals(emptyMap(), shuffler.atQuestion())
    assertEquals(emptyMap(), shuffler.atMelodyNote())
  }

  @Test
  fun `a per-question channel rolls on every question, always to a different preset`() {
    val shuffler =
      shuffler(
        presets(notes = listOf(1, 2)),
        PresetShuffleSettings(notes = PresetShuffleMode.PerQuestion),
      )
    var current = shuffler.atLevelStart().getValue(MidiChannel.Notes)

    repeat(6) {
      val next = shuffler.atQuestion().getValue(MidiChannel.Notes)
      assertNotEquals(current, next)
      current = next
    }
  }

  @Test
  fun `per-note falls back to per-question where notes do not sound one at a time`() {
    val modes = PresetShuffleSettings(notes = PresetShuffleMode.PerNote)
    val shuffler = shuffler(presets(notes = listOf(1, 2)), modes, perNoteSupported = false)
    shuffler.atLevelStart()

    assertEquals(emptyMap(), shuffler.atMelodyNote())
    assertEquals(setOf(MidiChannel.Notes), shuffler.atQuestion().keys)
  }

  @Test
  fun `per-note drives the melody notes where they do sound one at a time`() {
    val modes = PresetShuffleSettings(notes = PresetShuffleMode.PerNote)
    val shuffler = shuffler(presets(notes = listOf(1, 2)), modes, perNoteSupported = true)
    shuffler.atLevelStart()

    assertEquals(setOf(MidiChannel.Notes), shuffler.atMelodyNote().keys)
    assertEquals(emptyMap(), shuffler.atQuestion())
  }

  @Test
  fun `per-note stays a per-question schedule for the chord channels`() {
    val modes =
      PresetShuffleSettings(
        drone = PresetShuffleMode.PerNote,
        cadence = PresetShuffleMode.PerNote,
      )
    val shuffler =
      shuffler(
        presets(drone = listOf(40, 41), cadence = listOf(1, 2)),
        modes,
        perNoteSupported = true,
      )
    shuffler.atLevelStart()

    assertEquals(emptyMap(), shuffler.atMelodyNote())
    assertEquals(setOf(MidiChannel.Drone, MidiChannel.Cadence), shuffler.atQuestion().keys)
  }

  @Test
  fun `the manual roll moves every channel that has an alternative, whatever its schedule`() {
    val shuffler =
      shuffler(
        presets(notes = listOf(1, 2), drone = listOf(40, 41), cadence = listOf(7)),
        PresetShuffleSettings(),
      )
    val start = shuffler.atLevelStart()

    val rolled = shuffler.manual()

    assertEquals(setOf(MidiChannel.Notes, MidiChannel.Drone), rolled.keys)
    assertNotEquals(start.getValue(MidiChannel.Notes), rolled.getValue(MidiChannel.Notes))
    assertNotEquals(start.getValue(MidiChannel.Drone), rolled.getValue(MidiChannel.Drone))
  }

  @Test
  fun `a channel with one choice has nothing to roll to`() {
    val shuffler =
      shuffler(
        presets(notes = listOf(1)),
        PresetShuffleSettings(notes = PresetShuffleMode.PerQuestion),
      )
    shuffler.atLevelStart()

    assertEquals(emptyMap(), shuffler.atQuestion())
    assertEquals(emptyMap(), shuffler.manual())
  }
}
