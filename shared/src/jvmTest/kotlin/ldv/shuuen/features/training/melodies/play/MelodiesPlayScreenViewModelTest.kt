package ldv.shuuen.features.training.melodies.play

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.audio.engine.LoadedMelody
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
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
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.domain.ScaleConfig.ScaleItemState.ScalePitchState
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
      )
    advanceUntilIdle()
    assertEquals(6, engine.playedNotes.size)

    engine.playedNotes.clear()
    viewModel.rewindSequence()
    advanceUntilIdle()

    assertEquals(4, engine.playedNotes.size)
  }
}

private const val TestLevelId = "level"

private fun finiteRandomLevel(notesPerSequence: Int): MelodiesLevel =
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
}

private class FakeSettingsRepository : SettingsRepository {
  override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

  override suspend fun setSoundFontPath(path: String?) = Unit

  override suspend fun setPreset(channel: MidiChannel, preset: Preset) = Unit

  override suspend fun setVolume(channel: MidiChannel, value: Int) = Unit

  override suspend fun setMelodyOriginalVolumeBoost(value: Int) = Unit

  override suspend fun setInputMethod(inputMethod: InputMethod) = Unit

  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) = Unit

  override suspend fun setNoteNames(names: List<String>) = Unit

  override suspend fun setDegreeNames(names: List<String>) = Unit

  override suspend fun setCustomNoteNamesPreset(names: List<String>) = Unit

  override suspend fun setCustomDegreeNamesPreset(names: List<String>) = Unit
}

private class FakeMidiEngine : MidiEngine {
  val playedNotes = mutableListOf<Pair<Note, MidiChannel>>()

  override suspend fun initialize(): MidiEngineStatus = MidiEngineStatus.Ready

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int): Boolean {
    playedNotes += note to channel
    return true
  }

  override fun stopNote(note: Note, channel: MidiChannel): Boolean = true

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean = true

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean = true

  override fun stopAll(channel: MidiChannel?): Boolean = true

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
