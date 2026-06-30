package ldv.shuuen.core.music

import kotlin.test.Test
import kotlin.test.assertEquals

class KeySpellingTest {
  /** Spelling read out as a chromatic scale ascending from the root (degree order 1, ♭2, 2 …). */
  private fun spellFromRoot(rootOrdinal: Int, accidentalType: ScaleAccidentalType): List<String> {
    val byOrdinal = chromaticSpellingByOrdinal(rootOrdinal, accidentalType)
    return (0 until 12).map { offset -> byOrdinal[(rootOrdinal + offset).mod(12)].defaultLabel() }
  }

  @Test
  fun bMajorIsAllSharpsWithRaisedFourth() {
    assertEquals(
      listOf("B", "C", "C♯", "D", "D♯", "E", "E♯", "F♯", "G", "G♯", "A", "A♯"),
      spellFromRoot(11, ScaleAccidentalType.Sharps),
    )
  }

  @Test
  fun fMajorUsesFlatsWithNaturalRaisedFourth() {
    assertEquals(
      listOf("F", "G♭", "G", "A♭", "A", "B♭", "B", "C", "D♭", "D", "E♭", "E"),
      spellFromRoot(5, ScaleAccidentalType.Flats),
    )
  }

  @Test
  fun fSharpKeyStaysSingleAccidental() {
    assertEquals(
      listOf("F♯", "G", "G♯", "A", "A♯", "B", "B♯", "C♯", "D", "D♯", "E", "E♯"),
      spellFromRoot(6, ScaleAccidentalType.Sharps),
    )
  }

  @Test
  fun gFlatKeyStaysSingleAccidental() {
    assertEquals(
      listOf("G♭", "G", "A♭", "A", "B♭", "C♭", "C", "D♭", "D", "E♭", "F♭", "F"),
      spellFromRoot(6, ScaleAccidentalType.Flats),
    )
  }

  @Test
  fun cSharpKeyHasESharpAndBSharp() {
    assertEquals(
      listOf("C♯", "D", "D♯", "E", "E♯", "F♯", "G", "G♯", "A", "A♯", "B", "B♯"),
      spellFromRoot(1, ScaleAccidentalType.Sharps),
    )
  }

  @Test
  fun cFlatKeyFallsBackToSingleAccidentals() {
    assertEquals(
      listOf("C♭", "C", "D♭", "D", "E♭", "F♭", "F", "G♭", "G", "A♭", "A", "B♭"),
      spellFromRoot(11, ScaleAccidentalType.Flats),
    )
  }

  @Test
  fun clearWinnersResolveDeterministically() {
    // C and the sharp-side majors win sharps; flat-side majors win flats.
    assertEquals(ScaleAccidentalType.Sharps, decideAccidentalType(0, ScaleType.Major, allowSevenAccidentals = false))
    assertEquals(ScaleAccidentalType.Sharps, decideAccidentalType(7, ScaleType.Major, allowSevenAccidentals = false))
    assertEquals(ScaleAccidentalType.Flats, decideAccidentalType(5, ScaleType.Major, allowSevenAccidentals = false))
  }

  @Test
  fun minorKeysCountViaRelativeMajor() {
    // E minor → 1 sharp, D minor → 1 flat, A minor → naturals (sharps).
    assertEquals(ScaleAccidentalType.Sharps, decideAccidentalType(4, ScaleType.NaturalMinor, allowSevenAccidentals = false))
    assertEquals(ScaleAccidentalType.Flats, decideAccidentalType(2, ScaleType.NaturalMinor, allowSevenAccidentals = false))
    assertEquals(ScaleAccidentalType.Sharps, decideAccidentalType(9, ScaleType.NaturalMinor, allowSevenAccidentals = false))
  }

  @Test
  fun sevenAccidentalKeysAreExcludedByDefault() {
    // C#/Db forced to Db (5 flats), B/Cb forced to B (5 sharps) when 7-accidental keys are off.
    assertEquals(ScaleAccidentalType.Flats, decideAccidentalType(1, ScaleType.Major, allowSevenAccidentals = false))
    assertEquals(ScaleAccidentalType.Sharps, decideAccidentalType(11, ScaleType.Major, allowSevenAccidentals = false))
  }
}
