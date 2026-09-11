package ldv.shuuen.features.training.single.play

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.single.domain.SinglesLevel

class SinglesLevelQuizzerTest {
  private fun level(tuneInconsistencyCents: Int) =
    SinglesLevel(
      id = "test",
      name = "test",
      levelConfig =
        LevelConfig.Singles.Absolute(
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
          tuneInconsistencyCents = tuneInconsistencyCents,
        ),
      context = null,
      source = LevelSource.User,
      questionsNumber = null,
      range = NoteRange(Note(Pitch.C, 3), Note(Pitch.E, 4)),
    )

  @Test
  fun detuneStaysWithinTheLevelsTuneInconsistency() {
    val quizzer = SinglesLevelQuizzer(level(tuneInconsistencyCents = 20), random = Random(1))
    val rolled = mutableListOf<Int>()
    repeat(50) {
      val state = quizzer.quizState.value
      rolled += state.detuneCents
      quizzer.check(state.currentNote.pitch)
    }
    assertTrue(rolled.all { it in -20..20 }, "all rolls within ±20, got $rolled")
    assertTrue(rolled.any { it != 0 }, "the detune actually varies, got $rolled")
  }

  @Test
  fun detuneIsZeroWhenTheSettingIsOff() {
    val quizzer = SinglesLevelQuizzer(level(tuneInconsistencyCents = 0), random = Random(1))
    repeat(10) {
      val state = quizzer.quizState.value
      assertEquals(0, state.detuneCents)
      quizzer.check(state.currentNote.pitch)
    }
  }

  @Test
  fun detuneIsKeptWhileTheQuestionIsOpen() {
    val quizzer = SinglesLevelQuizzer(level(tuneInconsistencyCents = 20), random = Random(1))
    val beforeWrongGuess = quizzer.quizState.value.detuneCents
    val wrongPitch = if (quizzer.quizState.value.currentNote.pitch == Pitch.C) Pitch.E else Pitch.C
    quizzer.check(wrongPitch)
    assertEquals(beforeWrongGuess, quizzer.quizState.value.detuneCents)
  }
}
