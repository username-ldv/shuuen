package ldv.shuuen.features.training.chords.play

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.decideAccidentalType
import ldv.shuuen.core.music.generator.ChordNotesGenerator
import ldv.shuuen.core.music.generator.NaiveRandomChordGenerator
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.chordRepeatBudget
import ldv.shuuen.features.training.domain.LevelConfig

data class IncorrectChordsAnswer(val questionNumber: Int, val correctPitches: List<Pitch>)

/**
 * How a single guess landed. [Ignored] is an [ChordAnswerOrder.Any] re-press of an already-found
 * pitch class — no feedback. Ordered modes never ignore: a repeated class may genuinely be the
 * next expected note, so every press is judged against it.
 */
enum class ChordGuessResult {
  Correct,
  Incorrect,
  Ignored,
}

data class ChordsQuizState(
  val root: Pitch,
  val currentQuestionNumber: Int,
  val questionsNumber: Int?,
  /** The chord being asked, lowest note first. */
  val currentChord: List<Note>,
  /** Indexes into [currentChord] already answered for the current question. */
  val answeredNotes: Set<Int>,
  val correctAnswers: Int,
  val incorrectAnswers: List<IncorrectChordsAnswer>,
  /** Sharp/flat orientation chosen for [root]; re-decided whenever the root changes. */
  val accidentalType: ScaleAccidentalType,
)

class ChordsLevelQuizzer(
  val level: ChordsLevel,
  private val allowSevenAccidentalKeys: Boolean = false,
  private val random: Random = Random.Default,
) {
  // Scale rotation re-randomizes the tonic every N questions. Only a relative
  // (random-tonic) level rotates; an absolute level keeps its fixed root.
  // (for now)
  private val relativeConfig = level.levelConfig as? LevelConfig.Chords.Relative
  private val rotateEveryQuestions = relativeConfig?.rotateEveryQuestions

  // Drives the sharp/flat winner: minor counts via its relative major; others as a major key.
  private val scaleType: ScaleType =
    when (val c = level.levelConfig) {
      is LevelConfig.Chords.Relative -> c.scaleConfig.scaleType
      is LevelConfig.Chords.Absolute -> c.scales.first().scaleType
    }

  private var generator: ChordNotesGenerator
  private val _quizState: MutableStateFlow<ChordsQuizState>
  val quizState: StateFlow<ChordsQuizState>

  init {
    val root: Pitch
    when (val c = level.levelConfig) {
      is LevelConfig.Chords.Relative -> {
        root = Pitch.random()
        generator = generatorFor(root)
      }

      is LevelConfig.Chords.Absolute -> {
        root = c.scales.first().root
        generator = generatorFor(root)
      }
    }
    _quizState = MutableStateFlow(
      ChordsQuizState(
        root,
        currentQuestionNumber = 1,
        currentChord = generator.next(),
        answeredNotes = setOf(),
        correctAnswers = 0,
        incorrectAnswers = listOf(),
        questionsNumber = level.questionsNumber,
        accidentalType = accidentalTypeFor(root),
      )
    )
    quizState = _quizState.asStateFlow()
  }

  /** Sharp/flat orientation for [root], re-rolled (randomly for ambiguous keys) on each root change. */
  private fun accidentalTypeFor(root: Pitch): ScaleAccidentalType =
    decideAccidentalType(root.ordinal, scaleType, allowSevenAccidentalKeys, random)

  fun check(pitch: Pitch): ChordGuessResult {
    val current = quizState.value
    val chord = current.currentChord

    // Which chord notes this press answers. [ChordAnswerOrder.Any] resolves every octave copy of
    // the pitch class at once (copies are all-or-nothing, so checking one is checking all); the
    // ordered modes accept only the next note from their end — answered notes fill contiguously
    // from that end, so the expected index follows from the answered count.
    val answeredByPress: List<Int> =
      when (level.answerOrder) {
        ChordAnswerOrder.Any -> {
          val copies = chord.indices.filter { chord[it].pitch == pitch }
          if (copies.isNotEmpty() && copies.first() in current.answeredNotes) {
            return ChordGuessResult.Ignored
          }
          copies
        }

        ChordAnswerOrder.FromBottom -> {
          val next = current.answeredNotes.size
          if (next < chord.size && chord[next].pitch == pitch) listOf(next) else emptyList()
        }

        ChordAnswerOrder.FromTop -> {
          val next = chord.size - 1 - current.answeredNotes.size
          if (next >= 0 && chord[next].pitch == pitch) listOf(next) else emptyList()
        }
      }

    if (answeredByPress.isEmpty()) {
      _quizState.update {
        val isDupe =
          it.incorrectAnswers.any { answer -> answer.questionNumber == it.currentQuestionNumber }
        if (!isDupe) {
          it.copy(
            incorrectAnswers = it.incorrectAnswers + IncorrectChordsAnswer(
              it.currentQuestionNumber, it.currentChord.map { note -> note.pitch }
            )
          )
        } else it
      }
      return ChordGuessResult.Incorrect
    }

    val answered = current.answeredNotes + answeredByPress
    if (answered.size < chord.size) {
      _quizState.update { it.copy(answeredNotes = answered) }
      return ChordGuessResult.Correct
    }

    // The chord is complete: score the question and advance, rotating the tonic when due.
    val nextQuestionNumber = current.currentQuestionNumber + 1
    val nextRoot = rootForQuestion(current.root, nextQuestionNumber)
    val rootChanged = nextRoot != current.root
    if (rootChanged) generator = generatorFor(nextRoot)
    // Re-roll the sharp/flat orientation only when the root actually changes.
    val nextAccidentalType =
      if (rootChanged) accidentalTypeFor(nextRoot) else current.accidentalType
    _quizState.update { quizState ->
      val count =
        if (quizState.incorrectAnswers.any { it.questionNumber == quizState.currentQuestionNumber }) 0 else 1
      quizState.copy(
        root = nextRoot,
        correctAnswers = quizState.correctAnswers + count,
        currentQuestionNumber = nextQuestionNumber,
        currentChord = generator.next(),
        answeredNotes = setOf(),
        accidentalType = nextAccidentalType,
      )
    }
    return ChordGuessResult.Correct
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

  private fun generatorFor(root: Pitch): ChordNotesGenerator {
    val allowedNotes =
      when (val c = level.levelConfig) {
        is LevelConfig.Chords.Relative -> {
          val allowedDegrees = c.scaleConfig.degreeStates.filter { it.active }.map { it.degree }
          (level.range.from..level.range.to).filter {
            allowedDegrees.any { degree -> degree == root.asRoot(it.pitch) }
          }
        }

        is LevelConfig.Chords.Absolute -> {
          val allowedPitches = c.scales.first().pitchStates.filter { it.active }.map { it.pitch }
          (level.range.from..level.range.to).filter {
            allowedPitches.any { pitch -> pitch == it.pitch }
          }
        }
      }
    return NaiveRandomChordGenerator(
      allowedNotes = allowedNotes,
      minSize = level.chordSize.min,
      maxSize = level.chordSize.max,
      maxRepeatsForSize =
        when (level.answerOrder) {
          // One press resolves every copy of a class at once, so Any caps how many octave
          // duplicates a chord may hide.
          ChordAnswerOrder.Any -> ::chordRepeatBudget
          // Ordered answering names each octave copy in turn, so duplicates stay unrestricted —
          // a small scale can still fill a large chord across octaves.
          ChordAnswerOrder.FromBottom, ChordAnswerOrder.FromTop -> null
        },
      random = random,
    )
  }
}
