package ldv.shuuen.features.training.melodies.play

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.audio.engine.LoadedMelody
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetCutoffScope
import kotlin.test.assertFalse
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.settings.MidiLevelOptions
import ldv.shuuen.features.training.melodies.domain.MidiKey
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.audio.engine.MelodyNote
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.ThemeSettings
import ldv.shuuen.features.training.common.LevelAccuracyStats
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.domain.ScaleConfig.ScaleItemState.ScalePitchState
import ldv.shuuen.features.training.level_end.domain.TrainingSession
import ldv.shuuen.features.training.level_end.domain.TrainingSessionRepository
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.course.domain.TrainingLevelResolver
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MidiContentResolver
import ldv.shuuen.features.training.melodies.domain.MidiFileSource
import ldv.shuuen.core.music.MidiTransposition
import ldv.shuuen.core.music.MidiTranspositionMode
import ldv.shuuen.features.training.single.domain.SinglesLevel

@OptIn(ExperimentalCoroutinesApi::class)
class MelodiesPlayScreenViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun rewindingFiniteSequencePlaysFromFourNotesBeforeTheCursor() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val viewModel =
      MelodiesPlayScreenViewModel(
        levelId = TestLevelId,
        levelResolver = FakeTrainingLevelResolver(finiteRandomLevel(notesPerSequence = 6)),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
        midiContentResolver = FakeMidiContentResolver(),
        settingsRepository = FakeSettingsRepository(),
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    advanceUntilIdle()
    assertEquals(6, engine.playedNotes.size)

    engine.playedNotes.clear()
    viewModel.rewindSequence()
    advanceUntilIdle()

    assertEquals(4, engine.playedNotes.size)
  }

  @Test
  fun rewindingFiniteSequenceImmediatelyStopsCurrentNoteAndStartsRewoundNote() =
    runTest(dispatcher) {
      val engine = FakeMidiEngine()
      val viewModel =
        MelodiesPlayScreenViewModel(
          levelId = TestLevelId,
            levelResolver = FakeTrainingLevelResolver(finiteRandomLevel(notesPerSequence = 6)),
          midiEngine = engine,
          player = FakeMidiFilePlayer(),
          midiContentResolver = FakeMidiContentResolver(),
          settingsRepository = FakeSettingsRepository(),
          trainingSessionRepository = FakeTrainingSessionRepository(),
          midiKeyboardInput = FakeMidiKeyboardInput(),
        )
      runCurrent()
      assertEquals(listOf("play:C4"), engine.events)

      viewModel.rewindSequence()

      assertEquals(listOf("play:C4", "stop:C4", "play:C4"), engine.events)
      runCurrent()
      assertEquals(listOf("play:C4", "stop:C4", "play:C4"), engine.events)
      advanceUntilIdle()
    }

  @Test
  fun playedNotesCarryDetunesWithinTheLevelsTuneInconsistency() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    MelodiesPlayScreenViewModel(
        levelId = TestLevelId,
        levelResolver =
          FakeTrainingLevelResolver(
            finiteRandomLevel(notesPerSequence = 12, tuneInconsistencyCents = 30)
          ),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
        midiContentResolver = FakeMidiContentResolver(),
        settingsRepository = FakeSettingsRepository(),
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    advanceUntilIdle()

    assertEquals(12, engine.playedDetunes.size)
    assertTrue(
      engine.playedDetunes.all { it in -30..30 },
      "all detunes within ±30, got ${engine.playedDetunes}",
    )
    // 12 independent draws from -30..30 are all zero with odds ~2e-22.
    assertTrue(engine.playedDetunes.any { it != 0 }, "the detune actually varies")
  }

  @Test
  fun playedNotesAreInTuneWhenTheSettingIsOff() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    MelodiesPlayScreenViewModel(
        levelId = TestLevelId,
        levelResolver = FakeTrainingLevelResolver(finiteRandomLevel(notesPerSequence = 6)),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
        midiContentResolver = FakeMidiContentResolver(),
        settingsRepository = FakeSettingsRepository(),
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    advanceUntilIdle()

    assertEquals(List(6) { 0 }, engine.playedDetunes)
  }

  @Test
  fun definedMidiTranspositionFromSettingsIsPassedToTheFilePlayer() = runTest(dispatcher) {
    val player = FakeMidiFilePlayer()
    val settings =
      FakeSettingsRepository(
        AppSettings(
          midiLevelOptions =
            MidiLevelOptions(
              transposition = MidiTransposition(mode = MidiTranspositionMode.Defined, semitones = 3)
            )
        )
      )
    MelodiesPlayScreenViewModel(
      levelId = TestLevelId,
      levelResolver = FakeTrainingLevelResolver(midiLevel()),
      midiEngine = FakeMidiEngine(),
      player = player,
      midiContentResolver = FakeMidiContentResolver(),
      settingsRepository = settings,
      trainingSessionRepository = FakeTrainingSessionRepository(),
      midiKeyboardInput = FakeMidiKeyboardInput(),
    )
    runCurrent()

    assertEquals(3, player.loadedOptions?.transpositionSemitones)
  }

  @Test
  fun labelledMidiLevelShowsItsTransposedKeyAndPlaysTheSharedContext() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val settings =
      FakeSettingsRepository(
        AppSettings(
          midiLevelOptions =
            MidiLevelOptions(
              transposition = MidiTransposition(mode = MidiTranspositionMode.Defined, semitones = 2),
              context = droneContext(),
            )
        )
      )
    val viewModel =
      MelodiesPlayScreenViewModel(
        levelId = TestLevelId,
        levelResolver = FakeTrainingLevelResolver(midiLevel(key = dMajor())),
        midiEngine = engine,
        player = FakeMidiFilePlayer(notes = listOf(MelodyNote(Note(Pitch.D, 4), tick = 0L))),
        midiContentResolver = FakeMidiContentResolver(),
        settingsRepository = settings,
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    // The file transport polls forever once playback starts, so only drain what is queued now.
    runCurrent()

    val state = viewModel.state.value
    assertEquals(Pitch.E, state.root)
    assertEquals("E major", state.keyLabel)
    assertTrue(state.hasContext)
    // The drone is built on the transposed tonic, not on the file's original key.
    assertEquals(Pitch.E, engine.playedChords.single().first.notes.first().pitch)
    // Stop the transport poll and the context, or runTest never finds the scheduler idle.
    viewModel.viewModelScope.cancel()
  }

  @Test
  fun unlabelledMidiLevelIgnoresTheSharedContext() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val settings =
      FakeSettingsRepository(AppSettings(midiLevelOptions = MidiLevelOptions(context = droneContext())))
    val viewModel =
      MelodiesPlayScreenViewModel(
        levelId = TestLevelId,
        levelResolver = FakeTrainingLevelResolver(midiLevel()),
        midiEngine = engine,
        player = FakeMidiFilePlayer(notes = listOf(MelodyNote(Note(Pitch.C, 4), tick = 0L))),
        midiContentResolver = FakeMidiContentResolver(),
        settingsRepository = settings,
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    // The file transport polls forever once playback starts, so only drain what is queued now.
    runCurrent()

    val state = viewModel.state.value
    assertEquals(null, state.root)
    assertEquals(null, state.keyLabel)
    assertFalse(state.hasContext)
    assertTrue(engine.playedChords.isEmpty())
    viewModel.viewModelScope.cancel()
  }
}

private fun dMajor(): MidiKey =
  MidiKey(
    tonic = Pitch.D,
    degrees = listOf(Degree.D1, Degree.D2, Degree.D3, Degree.D4, Degree.D5, Degree.D6, Degree.D7),
    scaleType = ScaleType.Major,
    accidentalType = ScaleAccidentalType.Sharps,
  )

/** One endless tonic drone with no setup melody: the simplest context that keeps sounding. */
private fun droneContext(): DegreeContext =
  DegreeContext(
    id = "drone",
    source = ContextSource.UserGlobal,
    nodes =
      listOf(
        DegreeContextNode(
          firstDegree = DegreeWithOctave(Degree.D1, 3),
          extraDegrees = emptyList(),
          sustain = Sustain.Endless,
          duration = ContextDuration.Endless,
          setupMelody = null,
          relativeDirection = DegreeDirection.Up,
        )
      ),
    name = "Drone",
  )

private const val TestLevelId = "level"

private fun finiteRandomLevel(
  notesPerSequence: Int,
  tuneInconsistencyCents: Int = 0,
): MelodiesLevel =
  MelodiesLevel(
    id = TestLevelId,
    name = "Finite",
    config =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.AbsoluteScaleConfig(
            root = Pitch.C,
            scaleType = ScaleType.Major,
            pitchStates = Pitch.entries.map { ScalePitchState(it, active = it == Pitch.C) },
          ),
        questionsNumber = 1,
        notesPerSequence = notesPerSequence,
        tempo = 60_000,
        range = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 4)),
        tuneInconsistencyCents = tuneInconsistencyCents,
      ),
    context = null,
    source = LevelSource.User,
  )

private fun midiLevel(key: MidiKey? = null): MelodiesLevel =
  MelodiesLevel(
    id = TestLevelId,
    name = "MIDI",
    config =
      LevelConfig.Melodies.Midi(
        midiSource =
          MidiFileSource.Backend(
            melodyId = 1,
            variantId = 2,
            fileName = "test.mid",
            downloadUrl = "https://example.test/test.mid",
          ),
        fileName = "test.mid",
        key = key,
      ),
    context = null,
    source = LevelSource.Imported,
  )

private class FakeTrainingLevelResolver(private val level: MelodiesLevel) : TrainingLevelResolver {
  override suspend fun resolveSingles(encodedReference: String): SinglesLevel = error("not implemented")

  override suspend fun resolveMelodies(encodedReference: String): MelodiesLevel = level

  override suspend fun resolveChords(encodedReference: String): ChordsLevel = error("not implemented")
}

private class FakeMidiContentResolver : MidiContentResolver {
  override suspend fun resolve(source: MidiFileSource): ByteArray = byteArrayOf(1)
}

private class FakeTrainingSessionRepository : TrainingSessionRepository {
  val savedSessions = mutableListOf<TrainingSession>()

  override suspend fun saveSession(session: TrainingSession) {
    savedSessions += session
  }

  override fun getSessionById(id: String): Flow<ResponseState<TrainingSession>> =
    flowOf(ResponseState.Error(IllegalStateException("not implemented")))

  override fun observeLatestSession(): Flow<TrainingSession?> = flowOf(null)

  override fun observeLevelAccuracyStats(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<LevelAccuracyStats> = flowOf(LevelAccuracyStats(windowSize = limit))

  override fun observeAttemptedLevelIds(flow: TrainingFlow): Flow<Set<String>> = flowOf(emptySet())

  override fun observeCompletedLevelIds(flow: TrainingFlow): Flow<Set<String>> = flowOf(emptySet())

  override suspend fun deleteLastLevelSession(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllLevelSessions(flow: TrainingFlow, levelId: String) = Unit

  override suspend fun deleteAllCourseSessions(courseId: Long) = Unit
}

private class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
  override val settings: Flow<AppSettings> = MutableStateFlow(initial)

  override suspend fun setBackendUrl(url: String?) = Unit

  override suspend fun setSoundFontPath(path: String?) = Unit

  override suspend fun setPresetChoices(channel: MidiChannel, presets: List<Preset>) = Unit

  override suspend fun setPresetShuffleMode(channel: MidiChannel, mode: PresetShuffleMode) = Unit

  override suspend fun setPresetVolume(preset: Preset, percent: Int) = Unit

  override suspend fun setPresetCutoff(preset: Preset, cutoff: Int) = Unit

  override suspend fun setPresetCutoffScope(preset: Preset, scope: PresetCutoffScope) = Unit

  override suspend fun setPerNoteShuffleOnImportedMelodies(value: Boolean) = Unit

  override suspend fun setVolume(channel: MidiChannel, value: Int) = Unit

  override suspend fun setMelodyOriginalVolumeBoost(value: Int) = Unit

  override suspend fun setBackingTrackVolume(value: Int) = Unit

  override suspend fun setBackingTrackMutesMelody(value: Boolean) = Unit

  override suspend fun setMidiLevelOptions(options: MidiLevelOptions) = Unit

  override suspend fun setInputMethod(inputMethod: InputMethod) = Unit

  override suspend fun setTheme(theme: ThemeSettings) = Unit

  override suspend fun setMidiRespectOctaves(value: Boolean) = Unit

  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) = Unit

  override suspend fun setLevelStatsWindow(value: Int) = Unit

  override suspend fun setNoteNames(names: List<String>) = Unit

  override suspend fun setDegreeNames(names: List<String>) = Unit

  override suspend fun setCustomNoteNamesPreset(names: List<String>) = Unit

  override suspend fun setCustomDegreeNamesPreset(names: List<String>) = Unit
}

private class FakeMidiKeyboardInput : MidiKeyboardInput {
  override val connectedDevices: StateFlow<List<String>> = MutableStateFlow(emptyList())

  override val events: SharedFlow<MidiKeyboardEvent> =
    MutableSharedFlow(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

private class FakeMidiEngine : MidiEngine {
  val events = mutableListOf<String>()
  val playedNotes = mutableListOf<Pair<Note, MidiChannel>>()
  val playedDetunes = mutableListOf<Int>()
  val playedChords = mutableListOf<Pair<Chord, MidiChannel>>()

  override suspend fun initialize(): MidiEngineStatus = MidiEngineStatus.Ready

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int, detuneCents: Int): Boolean {
    events += "play:${note.name}"
    playedNotes += note to channel
    playedDetunes += detuneCents
    return true
  }

  override fun stopNote(note: Note, channel: MidiChannel): Boolean {
    events += "stop:${note.name}"
    return true
  }

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean {
    playedChords += chord to channel
    return true
  }

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean = true

  override fun stopAll(channel: MidiChannel?): Boolean = true

  override fun setPitchBendRange(channel: MidiChannel, semitones: Int): Boolean = true

  override fun setPitchBend(channel: MidiChannel, semitones: Double): Boolean = true

  override fun setPreset(channel: MidiChannel, preset: Preset): Boolean = true

  override fun setCutoff(channel: MidiChannel, value: Int): Boolean = true

  override fun setVolume(channel: MidiChannel, value: Int): Boolean = true

  override fun availablePresets(): List<Preset> = emptyList()

  override fun close() = Unit
}

private class FakeMidiFilePlayer(private val notes: List<MelodyNote> = emptyList()) : MidiFilePlayer {
  var loadedOptions: MidiFilePlaybackOptions? = null

  override suspend fun load(
    bytes: ByteArray,
    options: MidiFilePlaybackOptions,
  ): LoadedMelody {
    loadedOptions = options
    return LoadedMelody(notes = notes, lengthTicks = notes.size.toLong(), lengthSeconds = notes.size.toDouble())
  }

  override fun play() = Unit

  override fun pause() = Unit

  override fun seekToTick(tick: Long) = Unit

  override fun seekBySeconds(deltaSeconds: Double) = Unit

  override fun positionTicks(): Long = 0L

  override fun positionSeconds(): Double = 0.0

  override fun isPlaying(): Boolean = false

  override fun release() = Unit
}
