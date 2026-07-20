package ldv.shuuen.features.training.melodies.play

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.InputMethod
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
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository

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
        levelRepository = FakeMelodiesRepository(finiteRandomLevel(notesPerSequence = 6)),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
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
          levelRepository = FakeMelodiesRepository(finiteRandomLevel(notesPerSequence = 6)),
          midiEngine = engine,
          player = FakeMidiFilePlayer(),
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
        levelRepository =
          FakeMelodiesRepository(
            finiteRandomLevel(notesPerSequence = 12, tuneInconsistencyCents = 30)
          ),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
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
        levelRepository = FakeMelodiesRepository(finiteRandomLevel(notesPerSequence = 6)),
        midiEngine = engine,
        player = FakeMidiFilePlayer(),
        settingsRepository = FakeSettingsRepository(),
        trainingSessionRepository = FakeTrainingSessionRepository(),
        midiKeyboardInput = FakeMidiKeyboardInput(),
      )
    advanceUntilIdle()

    assertEquals(List(6) { 0 }, engine.playedDetunes)
  }
}

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

private class FakeMelodiesRepository(private val level: MelodiesLevel) :
  MelodiesLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<MelodiesLevel>>> =
    flowOf(ResponseState.Success(listOf(level)))

  override fun getLevelById(id: String): Flow<ResponseState<MelodiesLevel>> =
    flowOf(ResponseState.Success(level))

  override suspend fun upsertLevel(level: MelodiesLevel) = Unit

  override suspend fun deleteLevel(id: String) = Unit
}

private class FakeTrainingSessionRepository : TrainingSessionRepository {
  val savedSessions = mutableListOf<TrainingSession>()

  override suspend fun saveSession(session: TrainingSession) {
    savedSessions += session
  }

  override fun getSessionById(id: String): Flow<ResponseState<TrainingSession>> =
    flowOf(ResponseState.Error(IllegalStateException("not implemented")))

  override fun observeLevelAccuracyStats(
    flow: TrainingFlow,
    levelId: String,
    limit: Int,
  ): Flow<LevelAccuracyStats> = flowOf(LevelAccuracyStats(windowSize = limit))
}

private class FakeSettingsRepository : SettingsRepository {
  override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

  override suspend fun setSoundFontPath(path: String?) = Unit

  override suspend fun setPreset(channel: MidiChannel, preset: Preset) = Unit

  override suspend fun setVolume(channel: MidiChannel, value: Int) = Unit

  override suspend fun setMelodyOriginalVolumeBoost(value: Int) = Unit

  override suspend fun setBackingTrackVolume(value: Int) = Unit

  override suspend fun setBackingTrackMutesMelody(value: Boolean) = Unit

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

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean = true

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean = true

  override fun stopAll(channel: MidiChannel?): Boolean = true

  override fun setPitchBendRange(channel: MidiChannel, semitones: Int): Boolean = true

  override fun setPitchBend(channel: MidiChannel, semitones: Double): Boolean = true

  override fun setPreset(channel: MidiChannel, preset: Preset): Boolean = true

  override fun setVolume(channel: MidiChannel, value: Int): Boolean = true

  override fun availablePresets(): List<Preset> = emptyList()

  override fun close() = Unit
}

private class FakeMidiFilePlayer : MidiFilePlayer {
  override suspend fun load(
    bytes: ByteArray,
    options: MidiFilePlaybackOptions,
  ): LoadedMelody = LoadedMelody(notes = emptyList(), lengthTicks = 0L, lengthSeconds = 0.0)

  override fun play() = Unit

  override fun pause() = Unit

  override fun seekToTick(tick: Long) = Unit

  override fun seekBySeconds(deltaSeconds: Double) = Unit

  override fun positionTicks(): Long = 0L

  override fun positionSeconds(): Double = 0.0

  override fun isPlaying(): Boolean = false

  override fun release() = Unit
}
