package ldv.shuuen.free_play

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
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
    val viewModel = FreePlayViewModel(engine, initialTonic = Pitch.C)
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
    val viewModel = FreePlayViewModel(engine, initialTonic = Pitch.C)
    advanceUntilIdle()

    viewModel.onAction(FreePlayAction.ToggleDrone(7))
    viewModel.onAction(FreePlayAction.ToggleDrone(7))

    assertEquals(listOf(Note(Pitch.G, 2) to MidiChannel.Drone), engine.playedNotes)
    assertEquals(listOf(Note(Pitch.G, 2) to MidiChannel.Drone), engine.stoppedNotes)
  }
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
