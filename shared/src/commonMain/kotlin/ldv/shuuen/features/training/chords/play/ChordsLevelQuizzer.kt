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
import ldv.shuuen.features.training.domain.LevelConfig

data class IncorrectChordsAnswer(val questionNumber: Int, val correctPitches: List<Pitch>)

/** How a single guess landed. [Ignored] is a re-press of an already-found pitch — no feedback. */
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
  /** Chord pitches already answered for the current question. */
  val foundPitches: Set<Pitch>,
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
        foundPitches = setOf(),
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

  /**
   * The chord pitches a guess is currently allowed to hit. With [ChordAnswerOrder.Any] every
   * unanswered pitch is fair game; the ordered modes only accept the next note from their end.
   * Ordered modes fill strictly from one end, so the answered notes are always the first (or last)
   * [ChordsQuizState.foundPitches].size chord notes and the next expected one follows from the count.
   */
  private fun acceptedPitches(state: ChordsQuizState): Set<Pitch> {
    val remainingCount = state.currentChord.size - state.foundPitches.size
    if (remainingCount <= 0) return emptySet()
    return when (level.answerOrder) {
      ChordAnswerOrder.Any ->
        state.currentChord.map { it.pitch }.toSet() - state.foundPitches

      ChordAnswerOrder.FromBottom ->
        setOf(state.currentChord[state.foundPitches.size].pitch)

      ChordAnswerOrder.FromTop ->
        setOf(state.currentChord[remainingCount - 1].pitch)
    }
  }

  fun check(pitch: Pitch): ChordGuessResult {
    val current = quizState.value
    if (pitch in current.foundPitches) return ChordGuessResult.Ignored
    if (pitch !in acceptedPitches(current)) {
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

    val found = current.foundPitches + pitch
    if (found.size < current.currentChord.size) {
      _quizState.update { it.copy(foundPitches = found) }
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
        foundPitches = setOf(),
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
      random = random,
    )
  }
}
