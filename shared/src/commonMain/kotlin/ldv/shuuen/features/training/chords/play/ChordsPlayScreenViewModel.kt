package ldv.shuuen.features.training.chords.play

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
import kotlinx.coroutines.awaitCancellation
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
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository
import ldv.shuuen.features.training.common.DegreeContextPlayer
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.common.components.KeyFlashRequest
import ldv.shuuen.features.training.level_end.domain.QuestionResult
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.level_end.domain.longestCleanRun

sealed interface ChordsQuizPhase {
  object LoadingContext : ChordsQuizPhase

  object AwaitingAnswer : ChordsQuizPhase

  /** [sessionId] points at the saved results; null when there was nothing worth saving. */
  data class Complete(val sessionId: String?) : ChordsQuizPhase
}

data class ChordsPlayScreenState(
    val levelData: ResponseState<ChordsLevel> = ResponseState.Loading,
    val phase: ChordsQuizPhase = ChordsQuizPhase.LoadingContext,
    val quizState: ChordsQuizState? = null,
)

/** How long a non-sustained chord rings before its release. */
val playChordDuration = 1800.milliseconds

class ChordsPlayScreenViewModel(
    levelId: String,
    levelRepository: ChordsLocalLevelRepository,
    val midiEngine: MidiEngine,
    settingsRepository: SettingsRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(ChordsPlayScreenState())
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

  private var degreeContextPlayer: DegreeContextPlayer? = null

  private var degreeContextJob: Job? = null
  private var readyStatusJob: Job? = null
  private var setupMelodyNotesIndicationJob: Job? = null
  private var playChordJob: Job? = null
  private var playMelodyJob: Job? = null

  private var quizzer: ChordsLevelQuizzer? = null
  private var sustainNotes = false
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

  init {
    Napier.v { "Started chords level with id: $levelId" }

    viewModelScope.launch {
      when (midiEngine.initialize()) {
        MidiEngineStatus.Ready -> {
          Napier.v { "Initialized MidiEngine" }
        }

        is MidiEngineStatus.Failed -> error("Failed MidiEngine audio initializition")
      }

      lateinit var level: ChordsLevel
      levelRepository.getLevelById(levelId).collect { responseState ->
        _state.update {
          it.copy(levelData = responseState)
        }
        if (responseState !is ResponseState.Success) return@collect
        level = responseState.result
      }
      val allowSevenAccidentalKeys = settingsRepository.settings.map { it.allowSevenAccidentalKeys }.first()
      sustainNotes = level.sustainNotes
      quizzer = ChordsLevelQuizzer(level, allowSevenAccidentalKeys)

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
          Napier.v { "cancelling the currently playing chord BECAUSE of the new node starting..." }
          playChordJob?.cancelAndJoin()
        }
        degreeContextPlayer?.questionAdvanced(if (isNewRoot) quizState.root else null)

        if (isNewRoot) {
          val player: DegreeContextPlayer = degreeContextPlayer ?: startContext(c, quizState.root)
          if (degreeContextPlayer == null) degreeContextPlayer = player

          lastHandledRoot = quizState.root
          degreeContextPlayer?.ready?.first { it }
        }

        Napier.v { "Playing chord ${quizState.currentChord}" }
        // The question is only answerable from here on, so timing starts with the chord, not with
        // the context playback that may precede it.
        if (sessionStartMark == null) sessionStartMark = TimeSource.Monotonic.markNow()
        questionStartMark = TimeSource.Monotonic.markNow()
        rootsPracticed += quizState.root
        playChord(quizState.currentChord)
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
   * Returns how the guess landed, or null if no quiz is active (caller should not flash).
   */
  fun userGuessed(index: Int, mode: InputMode): ChordGuessResult? {
    val quizzer = quizzer ?: return null
    val pitch =
        when (mode) {
          InputMode.Absolute -> Pitch.fromOrdinal(index)
          InputMode.Relative -> Degree.fromOffset(index).pitch(quizzer.quizState.value.root)
        }
    return userGuessed(pitch)
  }

  /**
   * Returns how the guess landed, or null if no quiz is active (caller should not flash).
   */
  fun userGuessed(pitch: Pitch): ChordGuessResult? {
    val quizzer = quizzer ?: return null

    // The question's start mark must be captured before check() advances the question: the
    // quiz-state collector can resume inline on the main dispatcher and re-arm the mark for the
    // next question before this function reads it again (which made every answer time ~0).
    val questionBefore = quizzer.quizState.value.currentQuestionNumber
    val startMark = questionStartMark
    val result = quizzer.check(pitch)
    // The guess that completes the chord advances the question; that's when the answer time lands.
    // It runs from the first time the question's chord sounded, so wrong tries and repeats count.
    if (quizzer.quizState.value.currentQuestionNumber != questionBefore) {
      startMark?.let { answerTimesMillis += it.elapsedNow().inWholeMilliseconds }
    }
    return result
  }

  fun repeatChord() {
    val chord = quizzer?.quizState?.value?.currentChord ?: return
    replayCount += 1
    playChord(chord)
  }

  /** Ends the session before its natural end, saving whatever was answered so far. */
  fun finishEarly() {
    viewModelScope.launch {
      completeSession(finishedEarly = true)
    }
  }

  /**
   * Gathers the session's statistics, saves them, and flips the phase to
   * [ChordsQuizPhase.Complete]. A session with no answered questions is not worth a results screen
   * — it completes with a null session id and the screen simply navigates back.
   */
  @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
  private suspend fun completeSession(finishedEarly: Boolean) {
    if (sessionSaved) return
    sessionSaved = true
    // A sustained chord would otherwise keep ringing over the results screen.
    playChordJob?.cancelAndJoin()

    val quizState = quizzer?.quizState?.value
    val level = (_state.value.levelData as? ResponseState.Success)?.result
    val answered = (quizState?.currentQuestionNumber ?: 1) - 1
    if (quizState == null || level == null || answered <= 0) {
      _state.update { it.copy(phase = ChordsQuizPhase.Complete(sessionId = null)) }
      return
    }

    // A wrong guess on the question that was still open when the session ended never got resolved,
    // so it doesn't count as an answered (missed) question.
    val missedQuestions =
      quizState.incorrectAnswers.map { it.questionNumber }.filter { it <= answered }.toSet()
    val session = TrainingSession(
      id = Uuid.generateV7().toString(),
      flow = TrainingFlow.Chords,
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
    _state.update { it.copy(phase = ChordsQuizPhase.Complete(sessionId = savedId)) }
  }

  /**
   * Plays [notes] as one chord. A sustained level holds the chord until the next question's chord
   * (or a replay) cancels it; otherwise it rings for [playChordDuration] and releases. Exclusive:
   * a new call cancels the previous playback and waits for its note-off to finish before the new
   * note-on, so a rapid replay can't let an earlier stop land after a later start.
   */
  private fun playChord(notes: List<Note>) {
    val previous = playChordJob
    playChordJob = viewModelScope.launch {
      previous?.cancelAndJoin()
      val chord = Chord(notes)
      try {
        midiEngine.playChord(chord)
        if (sustainNotes) awaitCancellation() else delay(playChordDuration)
      } finally {
        midiEngine.stopChord(chord)
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
        _state.update {
          it.copy(phase = if (ready) ChordsQuizPhase.AwaitingAnswer else ChordsQuizPhase.LoadingContext)
        }
      }
    }
    degreeContextJob = viewModelScope.launch {
      player.start()
    }
    setupMelodyNotesIndicationJob = viewModelScope.launch {
      player.setupMelodyNotes.collect { note ->
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
