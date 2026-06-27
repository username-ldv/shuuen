package ldv.shuuen.music

import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.DirectedDegree
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.constructSetupMelodyFlow
import ldv.shuuen.core.music.withTiming
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class MusicDomainTest {
  @Test
  fun wrapsPitchArithmetic() {
    assertEquals(Pitch.B, Pitch.C - 1)
    assertEquals(Pitch.C, Pitch.B + 1)
    assertEquals(Pitch.D, Pitch.C + 14)
  }

  @Test
  fun mapsNotesToMidiAndBack() {
    assertEquals(60, Note(Pitch.C, 4).midiIndex)
    assertEquals(Pitch.A, Note(21).pitch)
    assertEquals(1, Note(Pitch.A, 0).pianoKeyNumber)
    assertEquals("C4", Note(Pitch.C, 4).name)
  }

  @Test
  fun resolvesNextAndPreviousPitchOccurrences() {
    assertEquals(Note(Pitch.E, 4), Note(Pitch.C, 4).next(Pitch.E))
    assertEquals(Note(Pitch.C, 5), Note(Pitch.C, 4).next(Pitch.C))
    assertEquals(Note(Pitch.A, 3), Note(Pitch.C, 4).previous(Pitch.A))
    assertEquals(Note(Pitch.C, 3), Note(Pitch.C, 4).previous(Pitch.C))
  }

  @Test
  fun mapsDegreesFromTonic() {
    assertEquals(Degree.D3, Note(Pitch.E, 4).degree(Pitch.C))
    assertEquals(Pitch.G, Degree.D5.pitch(Pitch.C))
  }

  @Test
  fun buildsNaturalMinorScale() {
    assertEquals(
      listOf(Pitch.C, Pitch.D, Pitch.DSharp, Pitch.F, Pitch.G, Pitch.GSharp, Pitch.ASharp),
      Scale.naturalMinor(Pitch.C).pitches,
    )
  }

  @Test
  fun buildsMajorChord() {
    assertEquals(
      listOf(60, 64, 67),
      Chord.major(Note(Pitch.C, 4)).notes.map { it.midiIndex },
    )
  }

  @Test
  fun buildsSetupMelodyFromDirectedRelativeMelody() = runTest {
    val melody =
      RelativeMelody(
        firstDegree = DegreeWithOctave(Degree.D1, 4),
        extraDegrees =
          listOf(
            DirectedDegree(Degree.D3, DegreeDirection.Up),
            DirectedDegree(Degree.D1, DegreeDirection.Down),
            DirectedDegree(Degree.D5, DegreeDirection.Down),
          ),
      )

    assertEquals(
      listOf(60, 64, 60, 55),
      constructSetupMelodyFlow(Pitch.C, melody).toList().map { it.midiIndex },
    )
  }

  @Test
  fun calculatesTimingValues() {
    withTiming(120) {
      assertEquals(500.milliseconds, quarter())
      assertEquals(250.milliseconds, eighth())
    }
  }
}
