package ldv.shuuen.core.music.generator

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.core.music.Pitch

class WeightedMelodyGeneratorTest {
  private val cMajorPitches =
    listOf(Pitch.C, Pitch.D, Pitch.E, Pitch.F, Pitch.G, Pitch.A, Pitch.B)

  private fun generator(
    style: MelodyStyle,
    range: NoteRange = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 5)),
    allowedPitches: List<Pitch> = cMajorPitches,
    seed: Int = 42,
  ) =
    WeightedMelodyGenerator(
      style = style,
      root = Pitch.C,
      range = range,
      allowedPitches = allowedPitches,
      random = Random(seed),
    )

  private fun notes(generator: WeightedMelodyGenerator, atLeast: Int): List<TimedNote> {
    val notes = mutableListOf<TimedNote>()
    while (notes.size < atLeast) notes += generator.nextFigure()
    return notes
  }

  @Test
  fun steadyQuartersProducesOnlyAllowedQuarterNotes() {
    val generated = notes(generator(MelodyStyles.SteadyQuarters), atLeast = 200)

    assertTrue(generated.all { it.value == NoteValue.Quarter })
    assertTrue(generated.all { it.note.pitch in cMajorPitches })
    assertTrue(
      generated.all {
        it.note.midiIndex in Note(Pitch.C, 4).midiIndex..Note(Pitch.C, 5).midiIndex
      }
    )
  }

  @Test
  fun contourFiguresWalkAdjacentScaleNotes() {
    val runs =
      MelodyStyle(
        id = "test-runs",
        name = "Runs",
        description = "",
        tier = StyleTier.Beginner,
        figures =
          listOf(
            WeightedFigure(
              RhythmFigure(
                values = List(5) { NoteValue.Quarter },
                contour = listOf(1, 1, 1, 1),
              ),
              weight = 1.0,
            )
          ),
        noteWeights = NoteWeights.Uniform,
      )
    // A three-note ladder (C4, D4, E4) forces upward runs to reflect off the top edge.
    val ladder = listOf(Note(Pitch.C, 4), Note(Pitch.D, 4), Note(Pitch.E, 4))
    val generated =
      notes(
        generator(
          runs,
          range = NoteRange(Note(Pitch.C, 4), Note(Pitch.E, 4)),
          allowedPitches = listOf(Pitch.C, Pitch.D, Pitch.E),
        ),
        atLeast = 100,
      )

    generated.chunked(5).forEach { figure ->
      figure.zipWithNext().forEach { (a, b) ->
        val stepSize = abs(ladder.indexOf(a.note) - ladder.indexOf(b.note))
        assertEquals(1, stepSize, "Contour must move exactly one ladder step: $a -> $b")
      }
    }
  }

  @Test
  fun singableWeightsPreferStepsOverWideLeaps() {
    val generated = notes(generator(MelodyStyles.SmoothSteps), atLeast = 800)

    val intervals =
      generated.zipWithNext().map { (a, b) -> abs(a.note.midiIndex - b.note.midiIndex) }
    val steps = intervals.count { it in 1..2 }
    val wideLeaps = intervals.count { it >= 6 }
    assertTrue(
      steps > wideLeaps * 2,
      "Expected mostly stepwise motion, got $steps steps vs $wideLeaps wide leaps.",
    )
  }

  @Test
  fun uniformWeightsStillReachEveryAllowedNote() {
    val generated = notes(generator(MelodyStyles.SteadyQuarters), atLeast = 500)

    val reached = generated.map { it.note }.toSet()
    val expected =
      (Note(Pitch.C, 4)..Note(Pitch.C, 5)).filter { it.pitch in cMajorPitches }.toSet()
    assertEquals(expected, reached)
  }

  @Test
  fun throwsWhenNoNotesAreAllowed() {
    val generator = generator(MelodyStyles.SteadyQuarters, allowedPitches = emptyList())

    assertFailsWith<NoSuchElementException> { generator.nextFigure() }
  }

  @Test
  fun chordToneBoostPullsTheMelodyOntoTheActiveChord() {
    val boosted =
      MelodyStyle(
        id = "test-boost",
        name = "Boost",
        description = "",
        tier = StyleTier.Beginner,
        figures = listOf(WeightedFigure(RhythmFigure(listOf(NoteValue.Quarter)), 1.0)),
        noteWeights = NoteWeights(chordToneBoost = 8.0),
      )
    val generator = generator(boosted)
    generator.setActiveChord(setOf(Pitch.C, Pitch.E, Pitch.G))

    val generated = notes(generator, atLeast = 600)

    val chordShare =
      generated.count { it.note.pitch in setOf(Pitch.C, Pitch.E, Pitch.G) } /
        generated.size.toDouble()
    // Uniform picking over C major would land on C/E/G about 43% of the time (4 of 8 ladder
    // notes, C twice); an 8x boost should push it far above that.
    assertTrue(chordShare > 0.6, "Expected chord tones to dominate, got $chordShare.")
  }

  @Test
  fun chordLadderContoursWalkOnlyChordTones() {
    val arpeggios =
      MelodyStyle(
        id = "test-arpeggio",
        name = "Arpeggio",
        description = "",
        tier = StyleTier.Beginner,
        figures =
          listOf(
            WeightedFigure(
              RhythmFigure(
                values = List(3) { NoteValue.Quarter },
                contour = listOf(1, 1),
                ladder = FigureLadder.Chord,
              ),
              weight = 1.0,
            )
          ),
        noteWeights = NoteWeights.Uniform,
      )
    val generator = generator(arpeggios)
    generator.setActiveChord(setOf(Pitch.C, Pitch.E, Pitch.G))

    val generated = notes(generator, atLeast = 90)

    // Each figure's first note is a free pick; the two contour notes must be chord tones.
    generated.chunked(3).forEach { figure ->
      figure.drop(1).forEach { timed ->
        assertTrue(
          timed.note.pitch in setOf(Pitch.C, Pitch.E, Pitch.G),
          "Chord-ladder contour left the chord: ${timed.note}",
        )
      }
    }
  }

  @Test
  fun droneChordIsIgnored() {
    val boosted =
      MelodyStyle(
        id = "test-drone",
        name = "Drone",
        description = "",
        tier = StyleTier.Beginner,
        figures = listOf(WeightedFigure(RhythmFigure(listOf(NoteValue.Quarter)), 1.0)),
        noteWeights = NoteWeights(chordToneBoost = 50.0),
      )
    val generator = generator(boosted)
    // A single-pitch "chord" (a drone) must not hijack the whole melody.
    generator.setActiveChord(setOf(Pitch.C))

    val generated = notes(generator, atLeast = 300)

    val cShare = generated.count { it.note.pitch == Pitch.C } / generated.size.toDouble()
    assertTrue(cShare < 0.5, "A drone should not dominate the melody, C share was $cShare.")
  }
}
