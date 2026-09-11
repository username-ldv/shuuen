package ldv.shuuen.core.music.generator

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch

class WeightedChordGeneratorTest {
  private val cMajorPitches =
    listOf(Pitch.C, Pitch.D, Pitch.E, Pitch.F, Pitch.G, Pitch.A, Pitch.B)

  /** C major over two octaves: the ladder stacked shapes climb. */
  private val ladder =
    (Note(Pitch.C, 3)..Note(Pitch.C, 5)).filter { it.pitch in cMajorPitches }

  private fun generator(
    style: ChordStyle,
    minSize: Int = 2,
    maxSize: Int = 4,
    seed: Int = 42,
  ) =
    WeightedChordGenerator(
      allowedNotes = ladder,
      style = style,
      minSize = minSize,
      maxSize = maxSize,
      random = Random(seed),
    )

  private fun style(vararg figures: WeightedChordFigure) =
    ChordStyle(
      id = "test",
      name = "Test",
      description = "",
      tier = StyleTier.Beginner,
      figures = figures.toList(),
    )

  @Test
  fun triadStyleStacksLadderThirdsInRootPosition() {
    val generator = generator(style(WeightedChordFigure(ChordFigure.Stacked(listOf(0, 2, 4)), 1.0)))

    repeat(100) {
      val chord = generator.next()
      val indexes = chord.map { ladder.indexOf(it) }
      assertEquals(3, chord.size)
      assertEquals(listOf(2, 2), indexes.zipWithNext().map { (a, b) -> b - a })
    }
  }

  @Test
  fun shapesOutsideTheChordSizeRangeFallBackToFreePick() {
    // Triads (3 notes) can't fit a strict 2-note level; generation must still produce chords.
    val generator =
      generator(
        style(WeightedChordFigure(ChordFigure.Stacked(listOf(0, 2, 4)), 1.0)),
        minSize = 2,
        maxSize = 2,
      )

    repeat(50) {
      val chord = generator.next()
      assertEquals(2, chord.size)
      assertTrue(chord.all { it in ladder })
    }
  }

  @Test
  fun figureWeightsSteerTheMix() {
    val triad = ChordFigure.Stacked(listOf(0, 2, 4))
    val seventh = ChordFigure.Stacked(listOf(0, 2, 4, 6))
    val generator =
      generator(style(WeightedChordFigure(triad, 8.0), WeightedChordFigure(seventh, 1.0)))

    val sizes = List(400) { generator.next().size }
    val triads = sizes.count { it == 3 }
    val sevenths = sizes.count { it == 4 }
    assertTrue(
      triads > sevenths * 3,
      "Expected triads to dominate 8:1 weighting, got $triads vs $sevenths.",
    )
  }

  @Test
  fun freePickStyleKeepsTheOriginalRandomBehavior() {
    val generator = generator(style(WeightedChordFigure(ChordFigure.FreePick, 1.0)))

    repeat(100) {
      val chord = generator.next()
      assertTrue(chord.size in 2..4)
      assertTrue(chord.all { it in ladder })
      assertEquals(chord, chord.sortedBy { it.midiIndex })
    }
  }
}
