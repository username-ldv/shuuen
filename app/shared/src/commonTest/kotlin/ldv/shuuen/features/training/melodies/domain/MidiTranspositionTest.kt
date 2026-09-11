package ldv.shuuen.features.training.melodies.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MidiTranspositionTest {
  @Test
  fun definedModeResolvesToItsSelectedAmount() {
    val setting = MidiTransposition(MidiTranspositionMode.Defined, semitones = -4)

    assertEquals(-4, setting.resolve(Random(1)))
  }

  @Test
  fun randomModeIncludesTheFullSupportedRange() {
    val setting = MidiTransposition(MidiTranspositionMode.Random)
    val values = List(1_000) { setting.resolve(Random(it)) }.toSet()

    assertEquals((MinimumMidiTransposition..MaximumMidiTransposition).toSet(), values)
    assertTrue(0 in values)
  }

  @Test
  fun amountMustStayInsideTheSupportedRange() {
    assertFailsWith<IllegalArgumentException> {
      MidiTransposition(MidiTranspositionMode.Defined, MaximumMidiTransposition + 1)
    }
  }
}
