package ldv.shuuen.features.training.single.play

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.common.DegreeContextPlayer
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults

enum class AnswerColors(val color: Color) {
  Correct(Color(0xff32cc73)),
  Incorrect(Color(0xffe74d3c)),
}

/** Monotone flash used for setup-melody key highlights (colorful palette is a future option). */
// val SetupMelodyFlashColor = Color(0xFFD9D9DE)

/** A request from the VM for the screen to flash a key (used for setup-melody highlights). */
data class KeyFlashRequest(val index: Int, val color: Color)

sealed interface QuizPhase {
  object LoadingContext : QuizPhase

  object AwaitingAnswer : QuizPhase

  object Complete : QuizPhase
}

data class SinglesPlayScreenState(
    val levelData: ResponseState<SinglesLevel> = ResponseState.Loading,
    val phase: QuizPhase = QuizPhase.LoadingContext,
    val quizState: QuizState? = null,
)

val playNoteDuration = 1500.milliseconds

class SinglesPlayScreenViewModel(
    levelId: String,
    levelRepository: SinglesLocalLevelRepository,
    val midiEngine: MidiEngine,
) : ViewModel() {
  private val _state = MutableStateFlow(SinglesPlayScreenState())
  val state = _state.asStateFlow()

  private var degreeContextPlayer: DegreeContextPlayer? = null

  private var degreeContextJob: Job? = null
  private var readyStatusJob: Job? = null
  private var setupMelodyNotesIndicationJob: Job? = null
  private var playNoteJob: Job? = null
  private var playMelodyJob: Job? = null

  private var quizzer: SinglesLevelQuizzer? = null
  var lastHandledQuestion = 0
  var lastHandledRoot: Pitch? = null

  // Setup-melody highlights and answer feedback are both transient flashes driven by the screen
  // through PianoKeyboardState. The VM only emits which key/color to flash as the melody plays.
  private val _setupMelodyFlashes =
      MutableSharedFlow<KeyFlashRequest>(
          extraBufferCapacity = 8,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )
  val setupMelodyFlashes: SharedFlow<KeyFlashRequest> = _setupMelodyFlashes.asSharedFlow()

  init {
    Napier.v { "Started level with id: $levelId" }

    viewModelScope.launch {
      when (midiEngine.initialize()) {
        MidiEngineStatus.Ready -> {
          Napier.v { "Initialized MidiEngine" }
        }

        is MidiEngineStatus.Failed -> error("Failed MidiEngine audio initializition")
      }

      lateinit var level: SinglesLevel
      levelRepository.getLevelById(levelId).collect { responseState ->
        _state.update {
          it.copy(levelData = responseState)
        }
        if (responseState !is ResponseState.Success) return@collect
        level = responseState.result
      }
      quizzer = SinglesLevelQuizzer(level)

      val c = level.context

      require(c != null) { "context is null, but need context for now" }

      quizzer?.quizState?.collect { quizState ->
        _state.update { it.copy(quizState = quizState) }

        val isNewQuestion = quizState.currentQuestionNumber != lastHandledQuestion
        val isNewRoot = quizState.root != lastHandledRoot

        if (!isNewQuestion) return@collect

        if (quizState.currentQuestionNumber > (quizState.questionsNumber ?: Int.MAX_VALUE)) {
          _state.update { it.copy(phase = QuizPhase.Complete) }
          return@collect
        }

        lastHandledQuestion = quizState.currentQuestionNumber

        if (degreeContextPlayer?.isChangingNode(quizState.currentQuestionNumber, isNewRoot) ?: false) {
          Napier.v { "cancelling the currently playing note BECAUSE of the new node starting..." }
          playNoteJob?.cancelAndJoin()
        }
        degreeContextPlayer?.questionAdvanced(if (isNewRoot) quizState.root else null)
        Napier.v { "After questionAdvanced()" }

        if (isNewRoot) {
          val player: DegreeContextPlayer = degreeContextPlayer ?: startContext(c, quizState.root)
          if (degreeContextPlayer == null) degreeContextPlayer = player
          //          readyStatusJob?.cancel()
          //          degreeContextJob?.cancel()
          //          setupMelodyNotesIndicationJob?.cancel()

          //          _state.update { it.copy(phase = QuizPhase.LoadingContext) }

          lastHandledRoot = quizState.root
          degreeContextPlayer?.ready?.first { it }
        }

        Napier.v { "Playing ${quizState.currentNote}" }
        playNote(quizState.currentNote)
      }
    }
  }

  /**
   * Returns whether the guess was correct, or null if no quiz is active (caller should not flash).
   */
  fun userGuessed(pitch: Pitch): Boolean? {
    val quizzer = quizzer ?: return null

    // Correctness must be read before check() advances the question.
    val isCorrect = quizzer.quizState.value.currentNote.pitch == pitch
    quizzer.check(pitch)
    return isCorrect
  }

  fun userGuessed(degree: Degree) {}

  fun repeatNote() {
    val note = quizzer?.quizState?.value?.currentNote ?: return
    playNote(note)
  }

  /**
   * Plays [note] for [playNoteDuration], then stops it. Exclusive: a new call cancels the previous
   * playback and waits for its note-off to finish before the new note-on. Without the join, a rapid
   * repeat could let an earlier coroutine's stopNote land after a later note-on and cut it off —
   * both target the same MIDI note.
   */
  private fun playNote(note: Note) {
    val previous = playNoteJob
    playNoteJob = viewModelScope.launch {
      previous?.cancelAndJoin()
      try {
        midiEngine.playNote(note)
        delay(playNoteDuration)
      } finally {
        midiEngine.stopNote(note)
      }
    }
  }

  fun playSetupMelody() {
    val previous = playMelodyJob
    playMelodyJob = viewModelScope.launch {
      previous?.cancelAndJoin()
      degreeContextPlayer?.playSetupMelody()
    }
  }

  private fun startContext(context: DegreeContext, root: Pitch): DegreeContextPlayer {
    Napier.v { "Starting context with pitch $root" }
    val player = DegreeContextPlayer(midiEngine, context, root)

    readyStatusJob = viewModelScope.launch {
      player.ready.collect { ready ->
        Napier.v { "ready state: $ready" }
//        if (!ready) {
//        }
        _state.update {
          it.copy(phase = if (ready) QuizPhase.AwaitingAnswer else QuizPhase.LoadingContext)
        }
      }
    }
    degreeContextJob = viewModelScope.launch {
      Napier.v { "Starting player..." }
      player.start()
    }
    setupMelodyNotesIndicationJob = viewModelScope.launch {
      player.setupMelodyNotes.collect { note ->
        Napier.v { "got setup melody note $note" }
        if (note != null) {
          _setupMelodyFlashes.emit(
              KeyFlashRequest(note.pitch.ordinal, PianoKeyboardDefaults.MonochromePressedColor)
          )
        }
      }
    }
    return player
  }
}
