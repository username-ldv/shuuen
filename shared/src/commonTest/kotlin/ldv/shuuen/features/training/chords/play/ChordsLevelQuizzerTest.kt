package ldv.shuuen.features.training.chords.play

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig

class ChordsLevelQuizzerTest {
  /**
   * A fixed-root level whose allowed notes are exactly C3, E3, C4, E4, so a 4-note chord is
   * deterministic and duplicate pitch classes are guaranteed.
   */
  private fun level(order: ChordAnswerOrder, size: Int) =
    ChordsLevel(
      id = "test",
      name = "test",
      levelConfig =
        LevelConfig.Chords.Absolute(
          scales =
            listOf(
              ScaleConfig.AbsoluteScaleConfig(
                root = Pitch.C,
                scaleType = ScaleType.Major,
                pitchStates =
                  listOf(
                    ScaleConfig.ScaleItemState.ScalePitchState(Pitch.C, true),
                    ScaleConfig.ScaleItemState.ScalePitchState(Pitch.E, true),
                  ),
              )
            ),
        ),
      context = null,
      source = LevelSource.User,
      questionsNumber = 5,
      range = NoteRange(Note(Pitch.C, 3), Note(Pitch.E, 4)),
      chordSize = ChordSizeRange(size, size),
      sustainNotes = false,
      answerOrder = order,
    )

  @Test
  fun fromBottomNamesEachOctaveCopyInTurn() {
    val quizzer = ChordsLevelQuizzer(level(ChordAnswerOrder.FromBottom, size = 4), random = Random(1))
    assertEquals(listOf(Note(Pitch.C, 3), Note(Pitch.E, 3), Note(Pitch.C, 4), Note(Pitch.E, 4)),
      quizzer.quizState.value.currentChord)

    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.C))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.E))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.C))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.E))

    assertEquals(2, quizzer.quizState.value.currentQuestionNumber)
    assertEquals(1, quizzer.quizState.value.correctAnswers)
  }

  @Test
  fun fromBottomRejectsAnAnsweredClassThatIsNotTheNextNote()  {
    val quizzer = ChordsLevelQuizzer(level(ChordAnswerOrder.FromBottom, size = 4), random = Random(1))

    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.C))
    // C4 is in the chord but E3 is the next note from the bottom.
    assertEquals(ChordGuessResult.Incorrect, quizzer.check(Pitch.C))
    assertEquals(1, quizzer.quizState.value.incorrectAnswers.size)
  }

  @Test
  fun fromTopNamesTheChordDownwards() {
    val quizzer = ChordsLevelQuizzer(level(ChordAnswerOrder.FromTop, size = 4), random = Random(1))

    assertEquals(ChordGuessResult.Incorrect, quizzer.check(Pitch.C))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.E))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.C))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.E))
    assertEquals(ChordGuessResult.Correct, quizzer.check(Pitch.C))

    assertEquals(2, quizzer.quizState.value.currentQuestionNumber)
    // The wrong first press marked the question missed.
    assertEquals(0, quizzer.quizState.value.correctAnswers)
  }

  @Test
  fun anyOrderResolvesEveryCopyOfAClassAtOnce() {
    // Size 3 out of {C3, E3, C4, E4} always doubles one class and leaves the other single.
    val quizzer = ChordsLevelQuizzer(level(ChordAnswerOrder.Any, size = 3), random = Random(2))
    val chord = quizzer.quizState.value.currentChord
    assertEquals(3, chord.size)
    val byClass = chord.groupBy { it.pitch }
    val doubled = byClass.entries.first { it.value.size == 2 }.key
    val single = byClass.entries.first { it.value.size == 1 }.key

    assertEquals(ChordGuessResult.Correct, quizzer.check(doubled))
    assertEquals(2, quizzer.quizState.value.answeredNotes.size)
    // Both copies are already answered; a re-press is neither right nor wrong.
    assertEquals(ChordGuessResult.Ignored, quizzer.check(doubled))

    assertEquals(ChordGuessResult.Correct, quizzer.check(single))
    assertEquals(2, quizzer.quizState.value.currentQuestionNumber)
    assertEquals(1, quizzer.quizState.value.correctAnswers)
  }
}
