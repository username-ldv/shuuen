package ldv.shuuen.free_play

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.features.free_play.FreePlayAction
import ldv.shuuen.features.free_play.FreePlayViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FreePlayViewModelTest {
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
  fun pressingEnabledKeyEmitsMidiNote() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val viewModel =
      FreePlayViewModel(engine, FakeSettingsRepository(), FakeMidiKeyboardInput(), initialTonic = Pitch.C)
    advanceUntilIdle()

    viewModel.onAction(FreePlayAction.PressPitch(Pitch.C.ordinal))
    viewModel.onAction(FreePlayAction.ReleasePitch(Pitch.C.ordinal))

    assertEquals(listOf(Note(Pitch.C, 4) to MidiChannel.Notes), engine.playedNotes)
    assertEquals(listOf(Note(Pitch.C, 4) to MidiChannel.Notes), engine.stoppedNotes)
    assertTrue(viewModel.state.value.audioReady)
  }

  @Test
  fun togglingDroneStartsAndStopsDroneChannel() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val viewModel =
      FreePlayViewModel(engine, FakeSettingsRepository(), FakeMidiKeyboardInput(), initialTonic = Pitch.C)
    advanceUntilIdle()

    viewModel.onAction(FreePlayAction.ToggleDrone(7))
    viewModel.onAction(FreePlayAction.ToggleDrone(7))

    assertEquals(listOf(Note(Pitch.G, 2) to MidiChannel.Drone), engine.playedNotes)
    assertEquals(listOf(Note(Pitch.G, 2) to MidiChannel.Drone), engine.stoppedNotes)
  }

  @Test
  fun midiKeyboardKeysPressAndReleaseLikeOnScreenKeys() = runTest(dispatcher) {
    val engine = FakeMidiEngine()
    val keyboard = FakeMidiKeyboardInput()
    val viewModel =
      FreePlayViewModel(engine, FakeSettingsRepository(), keyboard, initialTonic = Pitch.C)
    advanceUntilIdle()

    // G5 = MIDI 79; free play folds it to the pitch-class key (G is in the enabled C minor
    // scale) and sounds that key's default octave.
    keyboard.emit(MidiKeyboardEvent.NoteOn(79, 100))
    advanceUntilIdle()
    keyboard.emit(MidiKeyboardEvent.NoteOff(79))
    advanceUntilIdle()

    assertEquals(listOf(Note(Pitch.G, 4) to MidiChannel.Notes), engine.playedNotes)
    assertEquals(listOf(Note(Pitch.G, 4) to MidiChannel.Notes), engine.stoppedNotes)
  }
}

private class FakeMidiKeyboardInput : MidiKeyboardInput {
  override val connectedDevices: StateFlow<List<String>> = MutableStateFlow(listOf("Fake keyboard"))

  private val _events =
    MutableSharedFlow<MidiKeyboardEvent>(
      extraBufferCapacity = 16,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  override val events: SharedFlow<MidiKeyboardEvent> = _events

  fun emit(event: MidiKeyboardEvent) {
    check(_events.tryEmit(event))
  }
}

private class FakeSettingsRepository : SettingsRepository {
  override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

  override suspend fun setSoundFontPath(path: String?) = Unit

  override suspend fun setPreset(channel: MidiChannel, preset: Preset) = Unit

  override suspend fun setVolume(channel: MidiChannel, value: Int) = Unit

  override suspend fun setMelodyOriginalVolumeBoost(value: Int) = Unit

  override suspend fun setInputMethod(inputMethod: InputMethod) = Unit

  override suspend fun setMidiRespectOctaves(value: Boolean) = Unit

  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) = Unit

  override suspend fun setNoteNames(names: List<String>) = Unit

  override suspend fun setDegreeNames(names: List<String>) = Unit

  override suspend fun setCustomNoteNamesPreset(names: List<String>) = Unit

  override suspend fun setCustomDegreeNamesPreset(names: List<String>) = Unit
}

private class FakeMidiEngine : MidiEngine {
  val playedNotes = mutableListOf<Pair<Note, MidiChannel>>()
  val stoppedNotes = mutableListOf<Pair<Note, MidiChannel>>()

  override suspend fun initialize(): MidiEngineStatus = MidiEngineStatus.Ready

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int): Boolean {
    playedNotes += note to channel
    return true
  }

  override fun stopNote(note: Note, channel: MidiChannel): Boolean {
    stoppedNotes += note to channel
    return true
  }

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean = true

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean = true

  override fun stopAll(channel: MidiChannel?): Boolean = true

  override fun setPreset(channel: MidiChannel, preset: Preset): Boolean = true

  override fun setVolume(channel: MidiChannel, value: Int): Boolean = true

  override fun availablePresets(): List<Preset> = emptyList()

  override fun close() = Unit
}
