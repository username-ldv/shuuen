package ldv.shuuen.core.music

import kotlin.random.Random

/**
 * A single-accidental spelling of a pitch: one letter (C..B) with at most one accidental. Kept
 * separate from any display string so the rendered labels can become user-customizable later
 * (7 naturals + 7 sharps + 7 flats = 21 atoms). Use [defaultLabel] for the built-in rendering.
 */
enum class Accidental(val semitoneShift: Int) {
  Flat(-1),
  Natural(0),
  Sharp(1),
}

data class SpelledPitch(
  /** Letter index: 0=C, 1=D, 2=E, 3=F, 4=G, 5=A, 6=B. */
  val letterIndex: Int,
  val accidental: Accidental,
) {
  /** Pitch class (0..11) this spelling sounds as. */
  val pitchOrdinal: Int
    get() = (LetterNaturalSemitone[letterIndex] + accidental.semitoneShift).mod(12)
}

private val LetterNames = listOf("C", "D", "E", "F", "G", "A", "B")

/** Natural pitch ordinal of each letter, indexed by letter index. */
private val LetterNaturalSemitone = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/**
 * Letter steps from the root letter for chromatic offsets 0..11, following the relative degree
 * labels 1 ♭2 2 ♭3 3 4 ♯4 5 ♭6 6 ♭7 7: ♯4 borrows the 4th's letter (raised), 5 takes the 5th's,
 * and so on. Uniform across major, minor, chromatic and custom — only the root spelling differs.
 */
private val LetterStepByOffset = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)

/** Letter of the root when the key is spelled with sharps / flats, indexed by root pitch ordinal. */
private val SharpRootLetter = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val FlatRootLetter = intArrayOf(0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 0)

/** Built-in rendering of a spelling; replace with a user label map when that lands. */
fun SpelledPitch.defaultLabel(): String =
  LetterNames[letterIndex] +
    when (accidental) {
      Accidental.Flat -> "♭"
      Accidental.Natural -> ""
      Accidental.Sharp -> "♯"
    }

/**
 * Spells all 12 chromatic pitches for a key rooted at [rootOrdinal] in the given [accidentalType]
 * orientation. The returned list is indexed by pitch ordinal (0 = C … 11 = B), each entry the
 * single-accidental spelling for that pitch in this key. Notes that would strictly need a double
 * accidental (the non-diatonic notes of 7♯/7♭ keys) fall back to their simplest single-accidental
 * enharmonic, keeping every result inside the 21-label set.
 */
fun chromaticSpellingByOrdinal(
  rootOrdinal: Int,
  accidentalType: ScaleAccidentalType,
): List<SpelledPitch> {
  val root = rootOrdinal.mod(12)
  val rootLetter =
    if (accidentalType == ScaleAccidentalType.Sharps) SharpRootLetter[root] else FlatRootLetter[root]

  val byOrdinal = arrayOfNulls<SpelledPitch>(12)
  for (offset in 0 until 12) {
    val letterIndex = (rootLetter + LetterStepByOffset[offset]) % 7
    val actual = (root + offset).mod(12)
    val natural = LetterNaturalSemitone[letterIndex]

    // Nearest signed semitone distance from the letter's natural pitch to the actual pitch.
    var diff = (actual - natural).mod(12)
    if (diff > 6) diff -= 12

    byOrdinal[actual] =
      when (diff) {
        0 -> SpelledPitch(letterIndex, Accidental.Natural)
        1 -> SpelledPitch(letterIndex, Accidental.Sharp)
        -1 -> SpelledPitch(letterIndex, Accidental.Flat)
        else -> simplestSpelling(actual, accidentalType) // would need a double accidental
      }
  }
  return byOrdinal.map { it!! }
}

/** Simplest single-accidental spelling of a pitch class: natural where possible, else per orientation. */
private fun simplestSpelling(ordinal: Int, accidentalType: ScaleAccidentalType): SpelledPitch {
  val naturalLetter = LetterNaturalSemitone.indexOf(ordinal)
  if (naturalLetter >= 0) return SpelledPitch(naturalLetter, Accidental.Natural)
  return if (accidentalType == ScaleAccidentalType.Sharps) {
    SpelledPitch(LetterNaturalSemitone.indexOf((ordinal - 1).mod(12)), Accidental.Sharp)
  } else {
    SpelledPitch(LetterNaturalSemitone.indexOf((ordinal + 1).mod(12)), Accidental.Flat)
  }
}

/**
 * Chooses the sharp/flat orientation for the key rooted at [rootOrdinal]. A key that is clearly
 * fewer accidentals one way wins automatically (G major → sharps, F major → flats). The three
 * enharmonic pitch classes (F♯/G♭, C♯/D♭, B/C♭) have no clear winner and pick randomly; by default
 * the 7-accidental side is excluded, so C♯/D♭ and B/C♭ resolve to their 5-accidental spelling unless
 * [allowSevenAccidentals] is set. Minor keys count via their relative major; chromatic/custom are
 * counted as a major key on the root.
 */
fun decideAccidentalType(
  rootOrdinal: Int,
  scaleType: ScaleType,
  allowSevenAccidentals: Boolean,
  random: Random = Random.Default,
): ScaleAccidentalType {
  val majorRoot =
    when (scaleType) {
      ScaleType.NaturalMinor -> (rootOrdinal + 3).mod(12)
      else -> rootOrdinal.mod(12)
    }
  val sharps = (majorRoot * 7).mod(12) // 0..11
  val flats = (majorRoot * 5).mod(12) // = (12 - sharps) % 12
  val cap = if (allowSevenAccidentals) 7 else 6
  val sharpOk = sharps in 1..cap
  val flatOk = flats in 1..cap

  return when {
    sharps == 0 -> ScaleAccidentalType.Sharps // C / natural key: spell chromatic notes as sharps
    sharpOk && flatOk -> if (random.nextBoolean()) ScaleAccidentalType.Sharps else ScaleAccidentalType.Flats
    flatOk -> ScaleAccidentalType.Flats
    else -> ScaleAccidentalType.Sharps
  }
}
