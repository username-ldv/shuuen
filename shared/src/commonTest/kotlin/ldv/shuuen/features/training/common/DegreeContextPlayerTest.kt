package ldv.shuuen.features.training.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.SetupMelody
import ldv.shuuen.core.music.SetupMelodyRepeat
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.chordAt

@OptIn(ExperimentalCoroutinesApi::class)
class DegreeContextPlayerTest {
  @Test
  fun onceSetupMelodyDoesNotAddMelodyDelayWhenSequenceRollsOver() = runTest {
    val engine = FakeMidiEngine()
    val player =
      DegreeContextPlayer(
        midiEngine = engine,
        context = rolloverContext(),
        startingRoot = Pitch.C,
        endlessPreMelody = 200.milliseconds,
        endlessAfterMelody = 300.milliseconds,
        beforeNotes = 100.milliseconds,
      )

    val startJob = launch { player.start() }
    try {
      advanceUntilIdle()

      // First node plays the one-note setup melody: 200 ms pre + 800 ms note + 300 ms after.
      assertEquals(1_300, currentTime)

      val transitionTimes =
        List(8) {
          val before = currentTime
          val job = launch { player.questionAdvanced() }
          advanceUntilIdle()
          assertTrue(job.isCompleted)
          currentTime - before
        }

      assertEquals(listOf(0L, 100L, 0L, 100L, 0L, 100L, 0L, 100L), transitionTimes)
      assertEquals(1, engine.playedNotes.size)
      assertEquals(5, engine.playedChords.size)
    } finally {
      startJob.cancel()
    }
  }

  @Test
  fun exposesTheSoundingNodeChordAndFollowsNodeChanges() = runTest {
    val engine = FakeMidiEngine()
    val context = rolloverContext()
    val player =
      DegreeContextPlayer(
        midiEngine = engine,
        context = context,
        startingRoot = Pitch.C,
        endlessPreMelody = 200.milliseconds,
        endlessAfterMelody = 300.milliseconds,
        beforeNotes = 100.milliseconds,
      )

    val startJob = launch { player.start() }
    try {
      advanceUntilIdle()
      assertEquals(context.chordAt(Pitch.C, 0), player.currentChord.value)

      // Each node lasts two questions: after two advances the frame is the second node's chord.
      repeat(2) {
        launch { player.questionAdvanced() }
        advanceUntilIdle()
      }
      assertEquals(context.chordAt(Pitch.C, 1), player.currentChord.value)
    } finally {
      startJob.cancel()
    }
  }
}

private fun rolloverContext(): DegreeContext =
  DegreeContext(
    id = "rollover",
    source = ContextSource.UserLocal,
    nodes =
      listOf(
        contextNode(
          firstDegree = Degree.D1,
          extraDegrees = listOf(Degree.D3, Degree.D5),
          setupMelody =
            SetupMelody(
              melody = RelativeMelody(firstDegree = DegreeWithOctave(Degree.D1, 4)),
              repeat = SetupMelodyRepeat.Once,
            ),
        ),
        contextNode(
          firstDegree = Degree.D4,
          extraDegrees = listOf(Degree.D6, Degree.D1),
          relativeDirection = DegreeDirection.Up,
        ),
        contextNode(
          firstDegree = Degree.D5,
          extraDegrees = listOf(Degree.D7, Degree.D2),
          relativeDirection = DegreeDirection.Up,
        ),
        contextNode(
          firstDegree = Degree.D1,
          extraDegrees = listOf(Degree.D3, Degree.D5),
          relativeDirection = DegreeDirection.Up,
        ),
      ),
  )

private fun contextNode(
  firstDegree: Degree,
  extraDegrees: List<Degree>,
  setupMelody: SetupMelody? = null,
  relativeDirection: DegreeDirection = DegreeDirection.Up,
): DegreeContextNode =
  DegreeContextNode(
    firstDegree = DegreeWithOctave(firstDegree, 2),
    extraDegrees = extraDegrees,
    sustain = Sustain.Endless,
    duration = ContextDuration.Finite(durationInQuestions = 2),
    setupMelody = setupMelody,
    relativeDirection = relativeDirection,
  )

private class FakeMidiEngine : MidiEngine {
  val playedNotes = mutableListOf<Pair<Note, MidiChannel>>()
  val playedChords = mutableListOf<Pair<Chord, MidiChannel>>()

  override suspend fun initialize(): MidiEngineStatus = MidiEngineStatus.Ready

  override fun playNote(note: Note, channel: MidiChannel, velocity: Int, detuneCents: Int): Boolean {
    playedNotes += note to channel
    return true
  }

  override fun stopNote(note: Note, channel: MidiChannel): Boolean = true

  override fun playChord(chord: Chord, channel: MidiChannel, velocity: Int): Boolean {
    playedChords += chord to channel
    return true
  }

  override fun stopChord(chord: Chord, channel: MidiChannel): Boolean = true

  override fun stopAll(channel: MidiChannel?): Boolean = true

  override fun setPitchBendRange(channel: MidiChannel, semitones: Int): Boolean = true

  override fun setPitchBend(channel: MidiChannel, semitones: Double): Boolean = true

  override fun setPreset(channel: MidiChannel, preset: Preset): Boolean = true

  override fun setVolume(channel: MidiChannel, value: Int): Boolean = true

  override fun availablePresets(): List<Preset> = emptyList()

  override fun close() = Unit
}
