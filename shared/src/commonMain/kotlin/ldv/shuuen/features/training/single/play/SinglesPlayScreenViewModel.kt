package ldv.shuuen.features.training.single.play

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.features.training.common.DegreeContextPlayer
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.level_end.domain.QuestionResult
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.level_end.domain.longestCleanRun
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.features.training.common.components.KeyFlashRequest
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults

enum class AnswerColors(val color: Color) {
  Correct(Color(0xff32cc73)),
  Incorrect(Color(0xffe74d3c)),
}

/** Monotone flash used for setup-melody key highlights (colorful palette is a future option). */
// val SetupMelodyFlashColor = Color(0xFFD9D9DE)

sealed interface QuizPhase {
  object LoadingContext : QuizPhase

  object AwaitingAnswer : QuizPhase

  /** [sessionId] points at the saved results; null when there was nothing worth saving. */
  data class Complete(val sessionId: String?) : QuizPhase
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
    settingsRepository: SettingsRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    midiKeyboardInput: MidiKeyboardInput,
) : ViewModel() {
  private val _state = MutableStateFlow(SinglesPlayScreenState())
  val state = _state.asStateFlow()

  /** The input component + interpretation mode chosen in settings. */
  val inputMethod: StateFlow<InputMethod> =
      settingsRepository.settings
          .map { it.inputMethod }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InputMethod())

  val musicLabels: StateFlow<MusicLabelSettings> =
      settingsRepository.settings
          .map { it.musicLabels }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicLabelSettings())

  // Read at the moment a MIDI key lands, so it must always hold the latest value: Eagerly.
  private val midiRespectOctaves: StateFlow<Boolean> =
      settingsRepository.settings
          .map { it.midiRespectOctaves }
          .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private var degreeContextPlayer: DegreeContextPlayer? = null

  private var degreeContextJob: Job? = null
  private var readyStatusJob: Job? = null
  private var setupMelodyNotesIndicationJob: Job? = null
  private var playNoteJob: Job? = null
  private var playMelodyJob: Job? = null

  private var quizzer: SinglesLevelQuizzer? = null
  var lastHandledQuestion = 0
  var lastHandledRoot: Pitch? = null

  // Session statistics, gathered as the quiz runs and saved when the level ends.
  private var sessionStartMark: TimeSource.Monotonic.ValueTimeMark? = null
  private var questionStartMark: TimeSource.Monotonic.ValueTimeMark? = null
  private val answerTimesMillis = mutableListOf<Long>()
  private val rootsPracticed = mutableSetOf<Pitch>()
  private var replayCount = 0
  private var sessionSaved = false

  // Setup-melody highlights and answer feedback are both transient flashes driven by the screen
  // through PianoKeyboardState. The VM only emits which key/color to flash as the melody plays.
  private val _setupMelodyFlashes =
      MutableSharedFlow<KeyFlashRequest>(
          extraBufferCapacity = 8,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )
  val setupMelodyFlashes: SharedFlow<KeyFlashRequest> = _setupMelodyFlashes.asSharedFlow()

  // Answer feedback for MIDI keyboard guesses: the screen flashes the input item matching the
  // played key's pitch class, exactly like it does for its own taps.
  private val _midiGuessFlashes =
      MutableSharedFlow<KeyFlashRequest>(
          extraBufferCapacity = 8,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )
  val midiGuessFlashes: SharedFlow<KeyFlashRequest> = _midiGuessFlashes.asSharedFlow()

  init {
    Napier.v { "Started level with id: $levelId" }

    viewModelScope.launch {
      midiKeyboardInput.events.collect { event ->
        if (event is MidiKeyboardEvent.NoteOn) midiKeyPressed(event.midiIndex)
      }
    }

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
      val allowSevenAccidentalKeys = settingsRepository.settings.map { it.allowSevenAccidentalKeys }.first()
      quizzer = SinglesLevelQuizzer(level, allowSevenAccidentalKeys)

      val c = level.context

      require(c != null) { "context is null, but need context for now" }

      quizzer?.quizState?.collect { quizState ->
        _state.update { it.copy(quizState = quizState) }

        val isNewQuestion = quizState.currentQuestionNumber != lastHandledQuestion
        val isNewRoot = quizState.root != lastHandledRoot

        if (!isNewQuestion) return@collect

        if (quizState.currentQuestionNumber > (quizState.questionsNumber ?: Int.MAX_VALUE)) {
          completeSession(finishedEarly = false)
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
        // The question is only answerable from here on, so timing starts with the note, not with
        // the context playback that may precede it.
        if (sessionStartMark == null) sessionStartMark = TimeSource.Monotonic.markNow()
        questionStartMark = TimeSource.Monotonic.markNow()
        rootsPracticed += quizState.root
        playNote(quizState.currentNote)
      }
    }
  }

  /**
   * Interprets a tapped input item into a guessed pitch and checks it.
   *
   * [index] is the item's own index in the active input component (a piano key or a circle item).
   * [mode] decides how it becomes a pitch: [InputMode.Absolute] reads it as a chromatic pitch
   * ordinal; [InputMode.Relative] reads it as a chromatic degree offset from the current root.
   *
   * Returns whether the guess was correct, or null if no quiz is active (caller should not flash).
   */
  fun userGuessed(index: Int, mode: InputMode): Boolean? {
    val quizzer = quizzer ?: return null
    val pitch =
        when (mode) {
          InputMode.Absolute -> Pitch.fromOrdinal(index)
          InputMode.Relative -> Degree.fromOffset(index).pitch(quizzer.quizState.value.root)
        }
    return userGuessed(pitch)
  }

  /**
   * Returns whether the guess was correct, or null if no quiz is active (caller should not flash).
   * [exactMidiIndex] is set for MIDI keyboard guesses when octaves are respected: the guess must
   * then also match the asked note's octave, not just its pitch class.
   */
  fun userGuessed(pitch: Pitch, exactMidiIndex: Int? = null): Boolean? {
    val quizzer = quizzer ?: return null

    // Correctness must be read before check() advances the question.
    val currentNote = quizzer.quizState.value.currentNote
    val isCorrect =
        currentNote.pitch == pitch &&
            (exactMidiIndex == null || currentNote.midiIndex == exactMidiIndex)
    if (isCorrect) {
      // Time to answer counts wrong tries and repeats: it runs from the first time the question's
      // note sounded until the correct answer landed.
      questionStartMark?.let { answerTimesMillis += it.elapsedNow().inWholeMilliseconds }
      questionStartMark = null
    }
    quizzer.check(pitch, exactMidiIndex)
    return isCorrect
  }

  /**
   * A key pressed on a connected MIDI keyboard answers like a tap on the input item of the key's
   * pitch class, regardless of the on-screen input method. With the respect-octaves setting on,
   * the exact key (octave included) must match the asked note.
   */
  private fun midiKeyPressed(midiIndex: Int) {
    if (_state.value.phase != QuizPhase.AwaitingAnswer) return
    val pitch = Pitch.fromOrdinal(midiIndex)
    val exactMidiIndex = midiIndex.takeIf { midiRespectOctaves.value }
    val correct = userGuessed(pitch, exactMidiIndex) ?: return
    val color = if (correct) AnswerColors.Correct.color else AnswerColors.Incorrect.color
    _midiGuessFlashes.tryEmit(KeyFlashRequest(pitch, color))
  }

  fun repeatNote() {
    val note = quizzer?.quizState?.value?.currentNote ?: return
    replayCount += 1
    playNote(note)
  }

  /** Ends the session before its natural end, saving whatever was answered so far. */
  fun finishEarly() {
    viewModelScope.launch {
      playNoteJob?.cancelAndJoin()
      completeSession(finishedEarly = true)
    }
  }

  /**
   * Gathers the session's statistics, saves them, and flips the phase to [QuizPhase.Complete].
   * A session with no answered questions is not worth a results screen — it completes with a null
   * session id and the screen simply navigates back.
   */
  @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
  private suspend fun completeSession(finishedEarly: Boolean) {
    if (sessionSaved) return
    sessionSaved = true

    val quizState = quizzer?.quizState?.value
    val level = (_state.value.levelData as? ResponseState.Success)?.result
    val answered = (quizState?.currentQuestionNumber ?: 1) - 1
    if (quizState == null || level == null || answered <= 0) {
      _state.update { it.copy(phase = QuizPhase.Complete(sessionId = null)) }
      return
    }

    // A wrong guess on the question that was still open when the session ended never got resolved,
    // so it doesn't count as an answered (missed) question.
    val missedQuestions =
      quizState.incorrectAnswers.map { it.questionNumber }.filter { it <= answered }.toSet()
    val session = TrainingSession(
      id = Uuid.generateV7().toString(),
      flow = TrainingFlow.Singles,
      levelId = level.id,
      levelName = level.name,
      completedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
      finishedEarly = finishedEarly,
      questionsAnswered = answered,
      notesTotal = answered,
      correctNotes = quizState.correctAnswers,
      missedNotes = missedQuestions.size,
      replays = replayCount,
      durationMillis = sessionStartMark?.elapsedNow()?.inWholeMilliseconds ?: 0L,
      avgAnswerMillis =
        answerTimesMillis.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
      avgDeltaMillis = null,
      bestStreak = longestCleanRun(answered, missedQuestions.map { it - 1 }.toSet()),
      keysPracticed = rootsPracticed.size,
      questionResults =
        (1..answered).map { q -> QuestionResult(q, 1, if (q in missedQuestions) 1 else 0) },
    )
    val savedId =
      runCatching { trainingSessionRepository.saveSession(session) }
        .fold(
          onSuccess = { session.id },
          onFailure = {
            Napier.w(it) { "Failed to save the training session" }
            null
          },
        )
    _state.update { it.copy(phase = QuizPhase.Complete(sessionId = savedId)) }
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
      degreeContextPlayer?.playSetupMelody(true)
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
              KeyFlashRequest(note.pitch, PianoKeyboardDefaults.MonochromePressedColor)
          )
        }
      }
    }
    return player
  }
}
