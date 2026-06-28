package ldv.shuuen.features.training.single.play

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.generator.NaiveRandomDegreeNoteGenerator
import ldv.shuuen.core.music.generator.NaiveRandomNoteGenerator
import ldv.shuuen.core.music.generator.NoteGenerator
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.single.domain.SinglesLevel

data class IncorrectSinglesAnswer(val questionNumber: Int, val correctPitch: Pitch)

data class QuizState(
  val root: Pitch,
  val currentQuestionNumber: Int,
  val questionsNumber: Int?,
  val currentNote: Note,
  val correctAnswers: Int,
  val incorrectAnswers: List<IncorrectSinglesAnswer>,
)

class SinglesLevelQuizzer(val level: SinglesLevel) {
  // Scale rotation re-randomizes the tonic every N questions. Only a relative
  // (random-tonic) level rotates; an absolute level keeps its fixed root.
  // (for now)
  private val relativeConfig = level.levelConfig as? LevelConfig.Singles.Relative
  private val rotateEveryQuestions = relativeConfig?.rotateEveryQuestions

  private var generator: NoteGenerator
  private val _quizState: MutableStateFlow<QuizState>
  val quizState: StateFlow<QuizState>

  init {
    val root: Pitch
    when (val c = level.levelConfig) {
      is LevelConfig.Singles.Relative -> {
        root = Pitch.random()
        generator = relativeGenerator(root)
      }

      is LevelConfig.Singles.Absolute -> {
        root = c.scales.first().root
        generator = NaiveRandomNoteGenerator(
          range = level.range,
          allowedPitches = c.scales.first().pitchStates.filter { it.active }.map { it.pitch })
      }
    }
    _quizState = MutableStateFlow(
      QuizState(
        root,
        currentQuestionNumber = 1,
        currentNote = generator.next(),
        correctAnswers = 0,
        incorrectAnswers = listOf(),
        questionsNumber = level.questionsNumber,
      )
    )
    quizState = _quizState.asStateFlow()
  }

  fun check(pitch: Pitch): Boolean {
    val correctNow = quizState.value.currentNote.pitch == pitch
    if (correctNow) {
      val current = quizState.value
      val nextQuestionNumber = current.currentQuestionNumber + 1
      val nextRoot = rootForQuestion(current.root, nextQuestionNumber)
      // currently only for relative
      if (nextRoot != current.root) generator = relativeGenerator(nextRoot)
      _quizState.update { quizState ->
        val count =
          if (quizState.incorrectAnswers.any { it.questionNumber == quizState.currentQuestionNumber }) 0 else 1
        quizState.copy(
          root = nextRoot,
          correctAnswers = quizState.correctAnswers + count,
          currentQuestionNumber = nextQuestionNumber,
          currentNote = generator.next(),
        )
      }
    } else {
      _quizState.update {
        val isDupe =
          it.incorrectAnswers.any { answer -> answer.questionNumber == it.currentQuestionNumber }
        Napier.v { "isDupe: $isDupe, incorrectAnswers: ${it.incorrectAnswers}" }
        if (!isDupe) {
          it.copy(
            incorrectAnswers = it.incorrectAnswers + IncorrectSinglesAnswer(
              it.currentQuestionNumber, it.currentNote.pitch
            )
          )
        } else it
      }
    }
    return correctNow
  }

  /** Tonic for [questionNumber]: a fresh random root when rotation is due, else [currentRoot]. */
  private fun rootForQuestion(currentRoot: Pitch, questionNumber: Int): Pitch {
    val rotate = rotateEveryQuestions?.takeIf { it >= 1 } ?: return currentRoot
    val dueForRotation = questionNumber > 1 && (questionNumber - 1) % rotate == 0
    if (!dueForRotation) return currentRoot
    // Force a different tonic so the scale actually moves and the context replays.
    var newRoot = Pitch.random()
    while (newRoot == currentRoot) newRoot = Pitch.random()
    return newRoot
  }

  private fun relativeGenerator(root: Pitch): NoteGenerator {
    val config = relativeConfig ?: error("relative generator requested for a non-relative level")
    return NaiveRandomDegreeNoteGenerator(
      root = root,
      range = level.range,
      allowedDegrees = config.scaleConfig.degreeStates.filter { it.active }.map { it.degree },
    )
  }
}