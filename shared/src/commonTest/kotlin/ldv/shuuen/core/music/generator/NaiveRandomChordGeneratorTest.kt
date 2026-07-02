package ldv.shuuen.core.music.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.features.training.chords.domain.chordRepeatBudget

class NaiveRandomChordGeneratorTest {
  /** C, D, E, G, A over two octaves — five pitch classes, ten notes. */
  private val pentatonicTwoOctaves =
    listOf(Pitch.C, Pitch.D, Pitch.E, Pitch.G, Pitch.A).flatMap {
      listOf(Note(it, 3), Note(it, 4))
    }

  @Test
  fun notesAreDistinctAndSortedAscending() {
    val generator =
      NaiveRandomChordGenerator(pentatonicTwoOctaves, minSize = 2, maxSize = 10, random = Random(1))
    repeat(50) {
      val chord = generator.next()
      assertEquals(chord.distinct().size, chord.size)
      assertEquals(chord.sortedBy { it.midiIndex }, chord)
    }
  }

  @Test
  fun repeatBudgetCapsDuplicatesAndInfeasibleSizesAreSkipped() {
    val generator =
      NaiveRandomChordGenerator(
        pentatonicTwoOctaves,
        minSize = 8,
        maxSize = 10,
        maxRepeatsForSize = ::chordRepeatBudget,
        random = Random(2),
      )
    repeat(50) {
      val chord = generator.next()
      // 9 or 10 notes would need 4-5 duplicates against a budget of 3, so only 8 is feasible.
      assertEquals(8, chord.size)
      val extraCopies = chord.size - chord.distinctBy { it.pitch }.size
      assertTrue(extraCopies <= chordRepeatBudget(chord.size))
    }
  }

  @Test
  fun pairsNeverRepeatAPitchClassUnderTheBudget() {
    val generator =
      NaiveRandomChordGenerator(
        pentatonicTwoOctaves,
        minSize = 2,
        maxSize = 2,
        maxRepeatsForSize = ::chordRepeatBudget,
        random = Random(3),
      )
    repeat(50) {
      val chord = generator.next()
      assertEquals(2, chord.size)
      assertEquals(2, chord.distinctBy { it.pitch }.size)
    }
  }

  @Test
  fun unlimitedRepeatsFillLargeChordsFromASmallScale() {
    val generator =
      NaiveRandomChordGenerator(pentatonicTwoOctaves, minSize = 10, maxSize = 10, random = Random(4))
    val chord = generator.next()
    assertEquals(10, chord.size)
    assertEquals(5, chord.distinctBy { it.pitch }.size)
  }

  @Test
  fun fallsBackToTheLargestBuildableSizeWhenTheRangeIsInfeasible() {
    // A single pitch class: under the Any-order budget even a pair is infeasible (it would need a
    // duplicate), so the generator degrades to a single note instead of failing.
    val onlyCs = listOf(Note(Pitch.C, 3), Note(Pitch.C, 4), Note(Pitch.C, 5))
    val generator =
      NaiveRandomChordGenerator(
        onlyCs,
        minSize = 2,
        maxSize = 4,
        maxRepeatsForSize = ::chordRepeatBudget,
        random = Random(5),
      )
    assertEquals(1, generator.next().size)
  }
}
