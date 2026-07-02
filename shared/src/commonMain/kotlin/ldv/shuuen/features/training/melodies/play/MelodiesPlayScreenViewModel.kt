package ldv.shuuen.features.training.melodies.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.readBytes
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.engine.MelodyNote
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.decideAccidentalType
import ldv.shuuen.core.music.generator.TimedNote
import ldv.shuuen.core.music.generator.WeightedMelodyGenerator
import ldv.shuuen.core.music.withTiming
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.features.training.common.DegreeContextPlayer
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.common.components.KeyFlashRequest
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.level_end.domain.QuestionResult
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.level_end.domain.longestCleanRun
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository

enum class MelodiesPlayMode {
  Midi,
  Random,
}

data class IncorrectMelodyAnswer(
  val questionNumber: Int,
  val noteIndex: Int,
  val expectedPitch: Pitch,
  val guessedPitch: Pitch,
)

/** The session is over; [sessionId] points at the saved results, null when nothing was saved. */
data class SessionCompletion(val sessionId: String?)

data class MelodiesPlayState(
  val title: String = "Melody",
  val isLoading: Boolean = true,
  val error: String? = null,
  val mode: MelodiesPlayMode = MelodiesPlayMode.Midi,

  /**
   * The note sequence currently being transcribed: the whole file in [MelodiesPlayMode.Midi], the
   * current question's sequence (or the growing endless stream) in [MelodiesPlayMode.Random].
   */
  val notes: List<MelodyNote> = emptyList(),
  val answerIndex: Int = 0,
  val answeredPitches: List<Pitch> = emptyList(),
  val correctAnswers: Int = 0,
  val incorrectAnswers: List<IncorrectMelodyAnswer> = emptyList(),

  /** Tonic of the random level (null for a MIDI melody, which has no tracked key). */
  val root: Pitch? = null,
  /** Sharp/flat orientation for [root]; re-decided whenever the root rotates. */
  val accidentalType: ScaleAccidentalType? = null,

  // Random-mode session; a Midi level is a single question spanning the whole file.
  val questionNumber: Int = 1,
  val questionsNumber: Int? = 1,
  val isEndless: Boolean = false,
  val isPlayingSequence: Boolean = false,
  val sequencePlaybackIndex: Int = -1,

  // Midi transport
  val lengthTicks: Long = 0L,
  val lengthSeconds: Double = 0.0,
  val positionTicks: Long = 0L,
  val positionSeconds: Double = 0.0,
  val isPlaying: Boolean = false,

  /** Set once when the session ends (naturally or early); the screen navigates on it. */
  val completion: SessionCompletion? = null,
) {
  /** Index of the note the playback is currently on, or -1 when nothing sounds. */
  val playbackNoteIndex: Int
    get() =
      when (mode) {
        MelodiesPlayMode.Midi -> notes.indexOfLast { it.tick <= positionTicks }
        MelodiesPlayMode.Random -> if (isPlayingSequence) sequencePlaybackIndex else -1
      }

  val isPlaybackActive: Boolean
    get() =
      when (mode) {
        MelodiesPlayMode.Midi -> isPlaying
        MelodiesPlayMode.Random -> isPlayingSequence
      }

  val progress: Float
    get() = if (lengthTicks > 0) (positionTicks.toFloat() / lengthTicks).coerceIn(0f, 1f) else 0f

  val quizProgress: Float
    get() =
      when {
        mode == MelodiesPlayMode.Midi ->
          if (notes.isNotEmpty()) (answerIndex.toFloat() / notes.size).coerceIn(0f, 1f) else 0f

        isEndless -> 0f

        else ->
          ((questionNumber - 1).toFloat() / (questionsNumber ?: questionNumber)).coerceIn(0f, 1f)
      }

  /** Incorrect answers of the question currently on screen, keyed by cell index. */
  val missedIndexes: Set<Int>
    get() =
      incorrectAnswers.filter { it.questionNumber == questionNumber }.map { it.noteIndex }.toSet()

  val isQuizComplete: Boolean
    get() =
      when (mode) {
        MelodiesPlayMode.Midi -> notes.isNotEmpty() && answerIndex >= notes.size
        MelodiesPlayMode.Random ->
          !isEndless && questionsNumber != null && questionNumber > questionsNumber
      }
}

private val pollInterval = 50.milliseconds
private const val SeekSeconds = 5.0

/** How far rewind jumps back in note-based melody playback. */
private const val RewindNotes = 4

/** How many upcoming notes the endless stream keeps generated ahead of playback. */
private const val StreamLookahead = 12

private data class ActivePlaybackNote(val runId: Int, val note: Note)

class MelodiesPlayScreenViewModel(
  private val levelId: String,
  levelRepository: MelodiesLocalLevelRepository,
  private val midiEngine: MidiEngine,
  private val player: MidiFilePlayer,
  private val settingsRepository: SettingsRepository,
  private val trainingSessionRepository: TrainingSessionRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(MelodiesPlayState())
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

  private var pollJob: Job? = null
  private var sequenceJob: Job? = null
  private var advanceJob: Job? = null
  private var contextJob: Job? = null
  private var setupMelodyIndicationJob: Job? = null
  private var playMelodyJob: Job? = null
  private var contextPlayer: DegreeContextPlayer? = null
  private var generator: WeightedMelodyGenerator? = null
  private var randomConfig: LevelConfig.Melodies.Random? = null
  private var allowSevenAccidentalKeys = false

  /** Finite-sequence cursor; ends at notes.size after playback and moves back on rewind. */
  private var sequenceIndex = 0

  private var playbackRunId = 0
  private var activePlaybackNote: ActivePlaybackNote? = null

  /** Endless stream cursor; survives pause/resume and moves back on rewind. */
  private var streamIndex = 0

  /** Highest stream index that has started sounding — guesses can't run ahead of it. */
  private var maxStartedIndex = -1

  // Session statistics, gathered as the quiz runs and saved when the level ends.
  private var sessionStartMark: TimeSource.Monotonic.ValueTimeMark? = null
  private val rootsPracticed = mutableSetOf<Pitch>()
  private var rewindCount = 0
  private var sessionSaved = false

  // Answer-delta tracking: how far behind the first full hearing the answers land (0 = real time).
  /** When the current question's sequence finished its first complete playback pass. */
  private var questionPlaybackEndMark: TimeSource.Monotonic.ValueTimeMark? = null
  /** Endless stream: when each note first started sounding, keyed by note index. */
  private val noteStartMarks = mutableMapOf<Int, TimeSource.Monotonic.ValueTimeMark>()
  /** One delta per finished unit: a sequence in finite Random mode, a note in the endless stream. */
  private val answerDeltasMillis = mutableListOf<Long>()

  // Setup-melody highlights and answer feedback are both transient flashes driven by the screen
  // through the input component's state. The VM only emits which key/color to flash as the
  // context's setup melody plays.
  private val _setupMelodyFlashes =
    MutableSharedFlow<KeyFlashRequest>(
      extraBufferCapacity = 8,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val setupMelodyFlashes: SharedFlow<KeyFlashRequest> = _setupMelodyFlashes.asSharedFlow()

  init {
    viewModelScope.launch {
      when (val status = midiEngine.initialize()) {
        MidiEngineStatus.Ready -> Napier.v { "MIDI engine ready for melodies player" }
        is MidiEngineStatus.Failed -> {
          _state.update { it.copy(isLoading = false, error = "Audio init failed: ${status.message}") }
          return@launch
        }
      }

      val level =
        when (val response = levelRepository.getLevelById(levelId)
          .first { it !is ResponseState.Loading }) {
          is ResponseState.Success -> response.result
          else -> {
            _state.update { it.copy(isLoading = false, error = "Couldn't load the level.") }
            return@launch
          }
        }

      when (val config = level.config) {
        is LevelConfig.Melodies.Midi -> startMidiMode(level, config)
        is LevelConfig.Melodies.Random -> startRandomMode(level, config)
      }
    }
  }

  // region Midi mode

  private suspend fun startMidiMode(level: MelodiesLevel, config: LevelConfig.Melodies.Midi) {
    val bytes = runCatching { config.file.readBytes() }.getOrNull()
    if (bytes == null || bytes.isEmpty()) {
      _state.update {
        it.copy(
          title = level.name,
          isLoading = false,
          error = "Couldn't read ${config.fileName}. Has the file moved?",
        )
      }
      return
    }

    val loaded =
      runCatching {
        player.load(
          bytes,
          MidiFilePlaybackOptions(useOriginalVelocities = config.useOriginalVelocities),
        )
      }
        .getOrElse { throwable ->
          Napier.w(throwable) { "Failed to load melody" }
          _state.update { it.copy(isLoading = false, error = "Couldn't load the MIDI file.") }
          return
        }
    if (loaded.notes.isEmpty()) {
      player.release()
      _state.update {
        it.copy(
          title = level.name,
          isLoading = false,
          error = "No note events were found in this MIDI file.",
        )
      }
      return
    }
    _state.update {
      it.copy(
        title = level.name,
        isLoading = false,
        mode = MelodiesPlayMode.Midi,
        notes = loaded.notes,
        lengthTicks = loaded.lengthTicks,
        lengthSeconds = loaded.lengthSeconds,
      )
    }
    // Play through at natural tempo by default.
    sessionStartMark = TimeSource.Monotonic.markNow()
    player.play()
    refreshTransportState()
    startPolling()
  }

  private fun startPolling() {
    pollJob?.cancel()
    pollJob =
      viewModelScope.launch {
        while (isActive) {
          refreshTransportState()
          delay(pollInterval)
        }
      }
  }

  fun togglePlayPause() {
    when (_state.value.mode) {
      MelodiesPlayMode.Midi -> {
        if (player.isPlaying()) {
          player.pause()
        } else {
          val length = _state.value.lengthTicks
          // If playback finished, restart from the top.
          if (length > 0 && player.positionTicks() >= length) player.seekToTick(0)
          player.play()
        }
        refreshTransportState()
      }

      MelodiesPlayMode.Random -> toggleStream()
    }
  }

  fun seekForward() {
    player.seekBySeconds(SeekSeconds)
    refreshTransportState()
  }

  fun seekBackward() {
    when (_state.value.mode) {
      MelodiesPlayMode.Midi -> {
        rewindCount += 1
        player.seekBySeconds(-SeekSeconds)
        refreshTransportState()
      }

      MelodiesPlayMode.Random ->
        if (_state.value.isEndless) rewindStream() else rewindSequence()
    }
  }

  fun seekToFraction(fraction: Float) {
    val length = _state.value.lengthTicks
    if (length > 0) player.seekToTick((length * fraction.coerceIn(0f, 1f)).toLong())
    refreshTransportState()
  }

  private fun refreshTransportState() {
    _state.update {
      it.copy(
        positionTicks = player.positionTicks(),
        positionSeconds = player.positionSeconds(),
        isPlaying = player.isPlaying(),
      )
    }
  }

  // endregion

  // region Random mode

  private suspend fun startRandomMode(level: MelodiesLevel, config: LevelConfig.Melodies.Random) {
    randomConfig = config
    allowSevenAccidentalKeys =
      settingsRepository.settings.map { it.allowSevenAccidentalKeys }.first()

    val root =
      when (val scale = config.scaleConfig) {
        is ScaleConfig.AbsoluteScaleConfig -> scale.root
        is ScaleConfig.RelativeScaleConfig -> Pitch.random()
      }
    val generator = generatorFor(config, root)
    this.generator = generator

    val notesPerSequence = config.notesPerSequence
    val isEndless = notesPerSequence == null
    val initialNotes =
      generateNotes(generator, notesPerSequence ?: StreamLookahead, startIndex = 0)
    if (initialNotes == null) {
      _state.update {
        it.copy(
          title = level.name,
          isLoading = false,
          error = "The scale and range produce no playable notes.",
        )
      }
      return
    }

    level.context?.let { context ->
      val player = DegreeContextPlayer(midiEngine, context, root)
      contextPlayer = player
      contextJob = viewModelScope.launch { player.start() }
      setupMelodyIndicationJob =
        viewModelScope.launch {
          player.setupMelodyNotes.collect { note ->
            if (note != null) {
              _setupMelodyFlashes.emit(
                KeyFlashRequest(note.pitch, PianoKeyboardDefaults.MonochromePressedColor)
              )
            }
          }
        }
    }

    _state.update {
      it.copy(
        title = level.name,
        isLoading = false,
        mode = MelodiesPlayMode.Random,
        notes = initialNotes,
        root = root,
        accidentalType = accidentalTypeFor(root),
        questionNumber = 1,
        questionsNumber = config.questionsNumber,
        isEndless = isEndless,
      )
    }

    sequenceIndex = 0
    streamIndex = 0
    maxStartedIndex = -1

    // The context (if any) sets the tonal ground first; the notes start once it settles.
    contextPlayer?.ready?.first { it }
    rootsPracticed += root
    sessionStartMark = TimeSource.Monotonic.markNow()
    if (isEndless) startStream() else playSequence()
  }

  private fun generatorFor(
    config: LevelConfig.Melodies.Random,
    root: Pitch,
  ): WeightedMelodyGenerator {
    val allowedPitches =
      when (val scale = config.scaleConfig) {
        is ScaleConfig.AbsoluteScaleConfig ->
          scale.pitchStates.filter { it.active }.map { it.pitch }

        is ScaleConfig.RelativeScaleConfig ->
          scale.degreeStates.filter { it.active }.map { it.degree.pitch(root) }
      }
    return WeightedMelodyGenerator(
      style = config.melodyStyle,
      root = root,
      range = config.range,
      allowedPitches = allowedPitches,
    )
  }

  /** Sharp/flat orientation for [root], re-rolled (randomly for ambiguous keys) on root change. */
  private fun accidentalTypeFor(root: Pitch): ScaleAccidentalType? {
    val scaleType = randomConfig?.scaleConfig?.scaleType ?: return null
    return decideAccidentalType(root.ordinal, scaleType, allowSevenAccidentalKeys, Random.Default)
  }

  /**
   * At least [count] notes of whole rhythm figures from [generator], or null when the config
   * allows no notes at all. Figures are kept intact so eighth pairs and stepwise runs never get
   * split across the lookahead boundary of the endless stream.
   */
  private fun generateFigures(generator: WeightedMelodyGenerator, count: Int): List<TimedNote>? =
    runCatching {
      val notes = mutableListOf<TimedNote>()
      while (notes.size < count) notes += generator.nextFigure()
      notes
    }.getOrNull()

  /** Exactly [count] random notes with synthetic ticks, or null when no notes are allowed. */
  private fun generateNotes(
    generator: WeightedMelodyGenerator,
    count: Int,
    startIndex: Int,
  ): List<MelodyNote>? =
    generateFigures(generator, count)?.take(count)?.toMelodyNotes(startIndex)

  private fun List<TimedNote>.toMelodyNotes(startIndex: Int): List<MelodyNote> =
    mapIndexed { i, timed ->
      MelodyNote(
        note = timed.note,
        tick = (startIndex + i).toLong(),
        durationQuarters = timed.value.quarters,
      )
    }

  /** Plays the current finite sequence at the level tempo, one note per its rhythm value. */
  private fun playSequence(startIndex: Int = 0) {
    val tempo = randomConfig?.tempo ?: return
    cancelSequencePlayback(updateState = false)
    val runId = ++playbackRunId
    sequenceJob =
      viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        val notes = _state.value.notes
        if (notes.isEmpty()) return@launch
        val firstIndex = startIndex.coerceIn(0, notes.lastIndex)
        sequenceIndex = firstIndex
        _state.update { it.copy(isPlayingSequence = true) }
        try {
          withTiming(tempo) {
            for (index in firstIndex..notes.lastIndex) {
              val melodyNote = notes[index]
              _state.update { it.copy(sequencePlaybackIndex = index) }
              maxStartedIndex = maxOf(maxStartedIndex, index)
              val activeNote = ActivePlaybackNote(runId, melodyNote.note)
              activePlaybackNote = activeNote
              try {
                midiEngine.playNote(melodyNote.note)
                delay(ofQuarters(melodyNote.durationQuarters))
              } finally {
                stopActivePlaybackNote(activeNote)
              }
              if (playbackRunId == runId) sequenceIndex = index + 1
            }
            // The first pass that plays through to the end is the delta reference: the melody has
            // now been fully heard once. Rewound passes finishing later don't move it.
            if (playbackRunId == runId && questionPlaybackEndMark == null) {
              questionPlaybackEndMark = TimeSource.Monotonic.markNow()
            }
          }
        } finally {
          if (playbackRunId == runId) {
            _state.update { it.copy(isPlayingSequence = false, sequencePlaybackIndex = -1) }
          }
        }
      }
  }

  /** Moves finite sequence playback back by [RewindNotes] notes and plays from there. */
  fun rewindSequence() {
    val current = _state.value
    if (
      current.mode != MelodiesPlayMode.Random ||
        current.isEndless ||
        current.isQuizComplete ||
        current.notes.isEmpty()
    ) {
      return
    }
    rewindCount += 1
    val currentIndex =
      if (current.isPlayingSequence && current.sequencePlaybackIndex >= 0) {
        current.sequencePlaybackIndex
      } else {
        sequenceIndex
      }
    val rewindTo = (currentIndex - RewindNotes).coerceAtLeast(0)
    playSequence(startIndex = rewindTo)
  }

  /** Replays the context's setup melody on demand; a no-op when the context has none. */
  fun playSetupMelody() {
    val previous = playMelodyJob
    playMelodyJob = viewModelScope.launch {
      previous?.cancelAndJoin()
      contextPlayer?.playSetupMelody(true)
    }
  }

  private fun advanceToNextQuestion() {
    val previous = advanceJob
    advanceJob =
      viewModelScope.launch {
        previous?.cancelAndJoin()
        cancelSequencePlayback()
        questionPlaybackEndMark = null

        val nextQuestion = _state.value.questionNumber + 1
        val questionsNumber = _state.value.questionsNumber
        if (questionsNumber != null && nextQuestion > questionsNumber) {
          _state.update { it.copy(questionNumber = nextQuestion) }
          completeSession(finishedEarly = false)
          return@launch
        }

        val config = randomConfig ?: return@launch
        val notesPerSequence = config.notesPerSequence ?: return@launch

        // Scale rotation: a new random tonic every N questions, mirroring SinglesLevelQuizzer.
        // Only a relative (random-tonic) scale rotates.
        val currentRoot = _state.value.root
        val newRoot =
          rootForQuestion(config, currentRoot, nextQuestion)
            ?.takeIf { it != currentRoot }
        if (newRoot != null) {
          generator = generatorFor(config, newRoot)
          rootsPracticed += newRoot
        }
        val generator = generator ?: return@launch
        val sequence = generateNotes(generator, notesPerSequence, startIndex = 0) ?: return@launch

        _state.update {
          it.copy(
            questionNumber = nextQuestion,
            notes = sequence,
            answerIndex = 0,
            answeredPitches = emptyList(),
            root = newRoot ?: it.root,
            accidentalType = if (newRoot != null) accidentalTypeFor(newRoot) else it.accidentalType,
          )
        }
        maxStartedIndex = -1
        sequenceIndex = 0

        // Passing the new root replays the context for it (and lets finite nodes rotate);
        // questionAdvanced waits until the context is ready again. No extra pause beyond that —
        // the next sequence should be heard the moment the last answer lands (like Singles).
        contextPlayer?.questionAdvanced(newRoot)
        playSequence()
      }
  }

  /** Tonic for [questionNumber]: a fresh random root when rotation is due, else null (keep). */
  private fun rootForQuestion(
    config: LevelConfig.Melodies.Random,
    currentRoot: Pitch?,
    questionNumber: Int,
  ): Pitch? {
    if (config.scaleConfig !is ScaleConfig.RelativeScaleConfig) return null
    val rotate = config.rotateEveryQuestions?.takeIf { it >= 1 } ?: return null
    val dueForRotation = questionNumber > 1 && (questionNumber - 1) % rotate == 0
    if (!dueForRotation) return null
    // Force a different tonic so the scale actually moves and the context replays.
    var newRoot = Pitch.random()
    while (newRoot == currentRoot) newRoot = Pitch.random()
    return newRoot
  }

  // endregion

  // region Endless stream

  /**
   * The endless stream: plays note after note at the level tempo, generating ahead as it goes.
   * [streamIndex] is the transport position — pause keeps it, rewind moves it back.
   */
  private fun startStream() {
    val tempo = randomConfig?.tempo ?: return
    cancelSequencePlayback(updateState = false)
    val runId = ++playbackRunId
    sequenceJob =
      viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        _state.update { it.copy(isPlayingSequence = true) }
        try {
          withTiming(tempo) {
            while (isActive && playbackRunId == runId) {
              val index = streamIndex
              ensureGeneratedUpTo(index + StreamLookahead)
              val melodyNote = _state.value.notes.getOrNull(index) ?: break
              _state.update { it.copy(sequencePlaybackIndex = index) }
              maxStartedIndex = maxOf(maxStartedIndex, index)
              // Delta reference per note: rewound notes keep their first-sounded time, so waiting
              // through a rewind still counts against the answer delta.
              noteStartMarks.getOrPut(index) { TimeSource.Monotonic.markNow() }
              val activeNote = ActivePlaybackNote(runId, melodyNote.note)
              activePlaybackNote = activeNote
              try {
                midiEngine.playNote(melodyNote.note)
                delay(ofQuarters(melodyNote.durationQuarters))
              } finally {
                stopActivePlaybackNote(activeNote)
              }
              // A rewind mid-note moved the cursor already; don't overwrite it.
              if (playbackRunId == runId && streamIndex == index) streamIndex = index + 1
            }
          }
        } finally {
          if (playbackRunId == runId) {
            _state.update { it.copy(isPlayingSequence = false, sequencePlaybackIndex = -1) }
          }
        }
      }
  }

  private fun toggleStream() {
    if (!_state.value.isEndless) return
    val running = sequenceJob?.isActive == true
    if (running) {
      cancelSequencePlayback()
    } else {
      startStream()
    }
  }

  private fun rewindStream() {
    if (!_state.value.isEndless) return
    rewindCount += 1
    val wasRunning = sequenceJob?.isActive == true
    streamIndex = (streamIndex - RewindNotes).coerceAtLeast(0)
    if (wasRunning) {
      startStream()
    } else {
      _state.update { it.copy(sequencePlaybackIndex = streamIndex) }
    }
  }

  private fun cancelSequencePlayback(updateState: Boolean = true) {
    val hadPlayback = sequenceJob != null || activePlaybackNote != null
    sequenceJob?.cancel()
    sequenceJob = null
    playbackRunId += 1
    stopActivePlaybackNote()
    if (updateState && hadPlayback) {
      _state.update { it.copy(isPlayingSequence = false, sequencePlaybackIndex = -1) }
    }
  }

  private fun stopActivePlaybackNote(activeNote: ActivePlaybackNote) {
    if (activePlaybackNote == activeNote) {
      activePlaybackNote = null
      midiEngine.stopNote(activeNote.note)
    }
  }

  private fun stopActivePlaybackNote() {
    activePlaybackNote?.let { activeNote ->
      activePlaybackNote = null
      midiEngine.stopNote(activeNote.note)
    }
  }

  /** Appends generated notes until [lastIndex] exists, so the strip always shows what's ahead. */
  private fun ensureGeneratedUpTo(lastIndex: Int) {
    val generator = generator ?: return
    val existing = _state.value.notes.size
    if (existing > lastIndex) return
    // Whole figures only: the stream may run slightly past the lookahead, never through a figure.
    val appended =
      generateFigures(generator, count = lastIndex + 1 - existing)?.toMelodyNotes(existing)
        ?: return
    _state.update { it.copy(notes = it.notes + appended) }
  }

  // endregion

  /**
   * Interprets a tapped input item into a guessed pitch and checks it.
   *
   * [index] is the item's own index in the active input component (a piano key or a circle item).
   * [mode] decides how it becomes a pitch: [InputMode.Absolute] reads it as a chromatic pitch
   * ordinal; [InputMode.Relative] reads it as a chromatic degree offset from the current root
   * (falling back to C when there is none, e.g. a MIDI melody, matching the on-screen labels).
   */
  fun userGuessed(index: Int, mode: InputMode): Boolean? {
    val pitch =
      when (mode) {
        InputMode.Absolute -> Pitch.fromOrdinal(index)
        InputMode.Relative -> Degree.fromOffset(index).pitch(_state.value.root ?: Pitch.C)
      }
    return userGuessed(pitch)
  }

  /**
   * Checks the guessed pitch against the awaited note. A correct guess advances to the next note
   * (and in Random mode to the next question after the last note); a wrong guess records at most
   * one miss per note and stays on it. Returns null when no quiz is active (caller should not
   * flash).
   */
  fun userGuessed(pitch: Pitch): Boolean? {
    val current = _state.value
    if (current.isLoading || current.error != null || current.isQuizComplete) return null
    // The endless stream reveals notes as they sound; there is nothing to answer past the last
    // note that has started playing.
    if (current.isEndless && current.answerIndex > maxStartedIndex) return null
    val answerNote = current.notes.getOrNull(current.answerIndex) ?: return null
    val isCorrect = answerNote.note.pitch == pitch

    var sequenceFinished = false
    _state.update { state ->
      val note = state.notes.getOrNull(state.answerIndex) ?: return@update state
      val alreadyMissed =
        state.incorrectAnswers.any {
          it.questionNumber == state.questionNumber && it.noteIndex == state.answerIndex
        }
      if (note.note.pitch == pitch) {
        val nextAnswerIndex = state.answerIndex + 1
        sequenceFinished = !state.isEndless && nextAnswerIndex >= state.notes.size
        state.copy(
          answerIndex = nextAnswerIndex,
          answeredPitches = state.answeredPitches + pitch,
          correctAnswers = state.correctAnswers + if (alreadyMissed) 0 else 1,
        )
      } else {
        if (alreadyMissed) {
          state
        } else {
          state.copy(
            incorrectAnswers =
              state.incorrectAnswers +
                IncorrectMelodyAnswer(
                  questionNumber = state.questionNumber,
                  noteIndex = state.answerIndex,
                  expectedPitch = note.note.pitch,
                  guessedPitch = pitch,
                ),
          )
        }
      }
    }

    if (isCorrect && current.isEndless) {
      // Note delta: from the note first sounding to its correct answer.
      noteStartMarks[current.answerIndex]?.let {
        answerDeltasMillis += it.elapsedNow().inWholeMilliseconds
      }
    }
    if (isCorrect && sequenceFinished && current.mode == MelodiesPlayMode.Random) {
      // Question delta: from the sequence's first full hearing to its last correct answer. A
      // sequence answered entirely before its playback even finished is real-time — delta 0.
      answerDeltasMillis += questionPlaybackEndMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
      advanceToNextQuestion()
    } else if (isCorrect && current.mode == MelodiesPlayMode.Midi && _state.value.isQuizComplete) {
      viewModelScope.launch { completeSession(finishedEarly = false) }
    }
    return isCorrect
  }

  /** Ends the session before its natural end, saving whatever was answered so far. */
  fun finishEarly() {
    viewModelScope.launch { completeSession(finishedEarly = true) }
  }

  /**
   * Gathers the session's statistics, saves them, and publishes [SessionCompletion]. A session
   * with no answered notes is not worth a results screen — it completes with a null session id
   * and the screen simply navigates back.
   */
  @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
  private suspend fun completeSession(finishedEarly: Boolean) {
    if (sessionSaved) return
    sessionSaved = true
    cancelSequencePlayback()
    if (_state.value.mode == MelodiesPlayMode.Midi) player.pause()

    val state = _state.value
    val results = questionResults(state)
    val notesTotal = results.sumOf { it.noteCount }
    if (state.isLoading || state.error != null || notesTotal <= 0) {
      _state.update { it.copy(completion = SessionCompletion(sessionId = null)) }
      return
    }

    val durationMillis = sessionStartMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
    val avgDeltaMillis =
      if (state.mode == MelodiesPlayMode.Midi) {
        // The file is the session's one question: the delta is the whole extra time beyond the
        // melody's own length. Meaningless for a partially answered file.
        (durationMillis - (state.lengthSeconds * 1000).toLong())
          .coerceAtLeast(0L)
          .takeUnless { finishedEarly }
      } else {
        answerDeltasMillis.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
      }
    val session = TrainingSession(
      id = Uuid.generateV7().toString(),
      flow = TrainingFlow.Melodies,
      levelId = levelId,
      levelName = state.title,
      completedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
      finishedEarly = finishedEarly,
      questionsAnswered = results.size,
      notesTotal = notesTotal,
      correctNotes = state.correctAnswers,
      missedNotes = results.sumOf { it.missedCount },
      replays = rewindCount,
      durationMillis = durationMillis,
      // A melody answer overlaps with listening to the rest of the sequence; the delta below is
      // the meaningful timing metric here.
      avgAnswerMillis = null,
      avgDeltaMillis = avgDeltaMillis,
      bestStreak = bestStreak(state),
      keysPracticed = rootsPracticed.size,
      questionResults = results,
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
    _state.update { it.copy(completion = SessionCompletion(sessionId = savedId)) }
  }

  /**
   * The session's answered questions. A MIDI melody and the endless stream are per-note (each
   * answered note is one entry); finite Random mode is per-sequence, with a partial entry for a
   * sequence that was still open when the session ended early. A wrong guess on a note that never
   * got resolved is dropped, matching how the live score only counts resolved notes.
   */
  private fun questionResults(state: MelodiesPlayState): List<QuestionResult> {
    if (state.mode == MelodiesPlayMode.Midi || state.isEndless) {
      val missedIndexes =
        state.incorrectAnswers
          .filter { it.noteIndex < state.answerIndex }
          .map { it.noteIndex }
          .toSet()
      return (0 until state.answerIndex).map { i ->
        QuestionResult(i + 1, 1, if (i in missedIndexes) 1 else 0)
      }
    }

    val notesPerSequence = randomConfig?.notesPerSequence ?: return emptyList()
    val completedQuestions =
      (state.questionNumber - 1).coerceAtMost(state.questionsNumber ?: Int.MAX_VALUE)
    val results = mutableListOf<QuestionResult>()
    for (q in 1..completedQuestions) {
      results +=
        QuestionResult(q, notesPerSequence, state.incorrectAnswers.count { it.questionNumber == q })
    }
    val inProgress = completedQuestions < (state.questionsNumber ?: Int.MAX_VALUE)
    if (inProgress && state.answerIndex > 0) {
      val q = state.questionNumber
      val missed =
        state.incorrectAnswers.count { it.questionNumber == q && it.noteIndex < state.answerIndex }
      results += QuestionResult(q, state.answerIndex, missed)
    }
    return results
  }

  /** Longest run of first-try-correct notes, walked in the order the notes were answered. */
  private fun bestStreak(state: MelodiesPlayState): Int {
    if (state.mode == MelodiesPlayMode.Midi || state.isEndless) {
      val missed =
        state.incorrectAnswers
          .filter { it.noteIndex < state.answerIndex }
          .map { it.noteIndex }
          .toSet()
      return longestCleanRun(state.answerIndex, missed)
    }

    val notesPerSequence = randomConfig?.notesPerSequence ?: return 0
    val results = questionResults(state)
    val totalNotes = results.sumOf { it.noteCount }
    val answeredQuestions = results.map { it.questionNumber }.toSet()
    val missedPositions =
      state.incorrectAnswers
        .filter {
          it.questionNumber in answeredQuestions &&
            (it.questionNumber < state.questionNumber || it.noteIndex < state.answerIndex)
        }
        .map { (it.questionNumber - 1) * notesPerSequence + it.noteIndex }
        .toSet()
    return longestCleanRun(totalNotes, missedPositions)
  }

  override fun onCleared() {
    pollJob?.cancel()
    cancelSequencePlayback(updateState = false)
    advanceJob?.cancel()
    contextJob?.cancel()
    setupMelodyIndicationJob?.cancel()
    playMelodyJob?.cancel()
    player.release()
  }
}
