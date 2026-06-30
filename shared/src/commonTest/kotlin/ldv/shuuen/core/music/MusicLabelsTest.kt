package ldv.shuuen.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class MusicLabelsTest {
  @Test
  fun spelledPitchUsesNaturalSharpFlatBlocks() {
    val custom = List(MusicLabelDefaults.NoteNames.size) { index -> "n$index" }

    assertEquals("n0", SpelledPitch(letterIndex = 0, accidental = Accidental.Natural).customLabel(custom))
    assertEquals("n7", SpelledPitch(letterIndex = 0, accidental = Accidental.Sharp).customLabel(custom))
    assertEquals("n14", SpelledPitch(letterIndex = 0, accidental = Accidental.Flat).customLabel(custom))
    assertEquals("n19", SpelledPitch(letterIndex = 5, accidental = Accidental.Flat).customLabel(custom))
  }

  @Test
  fun blankAndMissingCustomLabelsFallBackToDefaults() {
    assertEquals(
      MusicLabelDefaults.NoteNames,
      effectiveNoteNames(emptyList()),
    )

    assertEquals(
      listOf("Do", "D", "E", "F", "G", "A", "B"),
      effectiveNoteNames(listOf("Do", "")).take(7),
    )
  }

  @Test
  fun degreeLabelsUseChromaticOrder() {
    val custom = List(MusicLabelDefaults.DegreeNames.size) { index -> "d$index" }

    assertEquals(
      listOf("d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d10", "d11"),
      effectiveDegreeNames(custom),
    )
  }
}
