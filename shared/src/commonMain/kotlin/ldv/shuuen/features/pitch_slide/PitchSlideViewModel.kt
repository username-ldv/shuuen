package ldv.shuuen.features.pitch_slide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.Note

/** The slidable frequency span. */
internal const val PitchSlideMinHz = 80.0
internal const val PitchSlideMaxHz = 1200.0

/**
 * D#4 (~311.1 Hz) — the one note the screen ever plays. It sits at the log-center of the span,
 * so a ±24-semitone pitch wheel sweeps the whole 80–1200 Hz range from this single note-on.
 */
internal val PitchSlideCenterNote = Note(63)
internal val PitchSlideCenterHz = 440.0 * 2.0.pow((PitchSlideCenterNote.midiIndex - 69) / 12.0)
private const val BendRangeSemitones = 24

private val TargetPlayTime = 2.seconds

/** Breather between the target fading out and the player's bendable tone entering. */
private val GapAfterTarget = 700.milliseconds

/** Silence after the screen opens, before the first round's target plays. */
private val SilenceBeforeTarget = 1.seconds

data class PitchSlideState(
  val audioReady: Boolean = false,
  val errorMessage: String? = null,
  val round: Int = 1,
  val targetHz: Double = PitchSlideCenterHz,
  val currentHz: Double = PitchSlideCenterHz,
  val isUserToneSounding: Boolean = false,
  val isTargetPlaying: Boolean = false,
  /** Set once the round is answered: signed cents the guess missed the target by. */
  val errorCents: Double? = null,
) {
  val revealed: Boolean
    get() = errorCents != null

  /**
   * 0–10 score: 10.00 within 0.1 Hz of the target, 0 from 100 cents off, linear in between.
   * Derived from the frozen [errorCents] (not the live slider), so it doesn't change when the
   * player keeps sliding after the reveal.
   */
  val score: Double?
    get() = errorCents?.let { cents ->
      val answeredHz = targetHz * 2.0.pow(cents / 1200.0)
      when {
        abs(answeredHz - targetHz) < 0.1 -> 10.0
        abs(cents) >= 100.0 -> 0.0
        else -> 10.0 * (1.0 - abs(cents) / 100.0)
      }
    }
}

/**
 * Proof of concept of a dialed.gg-style pitch-recreation game. Each round: the target tone plays,
 * a short gap, then the player's bendable tone comes in on its own and keeps sounding while the
 * question is open — sliding the wave bends it, releasing just leaves it playing. Answering stops
 * the sound; after the reveal the wave is hold-to-hear, so the player can compare against target
 * replays and move on when ready. One note is held on the Notes channel (whatever preset it has)
 * and all movement is the channel's pitch wheel.
 */
class PitchSlideViewModel(private val midiEngine: MidiEngine) : ViewModel() {
  private val _state = MutableStateFlow(PitchSlideState(targetHz = randomTargetHz()))
  val state = _state.asStateFlow()

  private var sequenceJob: Job? = null

  init {
    viewModelScope.launch {
      when (val status = midiEngine.initialize()) {
        MidiEngineStatus.Ready -> {
          midiEngine.setPitchBendRange(MidiChannel.Notes, BendRangeSemitones)
          _state.update { it.copy(audioReady = true) }
          playTargetThenResume(silenceBefore = SilenceBeforeTarget)
        }

        is MidiEngineStatus.Failed ->
          _state.update { it.copy(errorMessage = "Audio init failed: ${status.message}") }
      }
    }
  }

  /** Replays the target, then brings the player's tone back in. */
  fun playTarget() {
    if (!_state.value.audioReady) return
    playTargetThenResume()
  }

  /**
   * The round's sound sequence: silence the player's tone, wait [silenceBefore], sound the target
   * for [TargetPlayTime], wait [GapAfterTarget], then start the player's tone at wherever the
   * slider sits.
   */
  private fun playTargetThenResume(silenceBefore: Duration = Duration.ZERO) {
    val previous = sequenceJob
    sequenceJob =
      viewModelScope.launch {
        previous?.cancelAndJoin()
        stopUserTone()
        if (silenceBefore > Duration.ZERO) delay(silenceBefore)
        _state.update { it.copy(isTargetPlaying = true) }
        try {
          midiEngine.setPitchBend(MidiChannel.Notes, bendFor(_state.value.targetHz))
          midiEngine.playNote(PitchSlideCenterNote)
          delay(TargetPlayTime)
        } finally {
          midiEngine.stopNote(PitchSlideCenterNote)
          _state.update { it.copy(isTargetPlaying = false) }
        }
        // An open question gets the persistent tone back; after the reveal it's hold-to-hear.
        if (!_state.value.revealed) {
          delay(GapAfterTarget)
          startUserTone()
        }
      }
  }

  private fun startUserTone() {
    if (_state.value.isUserToneSounding) return
    midiEngine.setPitchBend(MidiChannel.Notes, bendFor(_state.value.currentHz))
    midiEngine.playNote(PitchSlideCenterNote)
    _state.update { it.copy(isUserToneSounding = true) }
  }

  private fun stopUserTone() {
    if (!_state.value.isUserToneSounding) return
    midiEngine.stopNote(PitchSlideCenterNote)
    _state.update { it.copy(isUserToneSounding = false) }
  }

  /**
   * Finger down or dragging on the wave: bends the tone to [hz]. The slider always moves, but
   * while the round sequence is mid-flight (pre-target silence, target, gap) the tone itself
   * stays with the sequence — it comes in when the sequence finishes.
   */
  fun slideTo(hz: Double) {
    val current = _state.value
    if (!current.audioReady || current.isTargetPlaying) return
    val clamped = hz.coerceIn(PitchSlideMinHz, PitchSlideMaxHz)
    if (current.isUserToneSounding) midiEngine.setPitchBend(MidiChannel.Notes, bendFor(clamped))
    _state.update { it.copy(currentHz = clamped) }
    if (current.revealed || sequenceJob?.isActive != true) startUserTone()
  }

  /** Finger up. While the question is open the tone keeps playing; after the reveal it stops. */
  fun slideReleased() {
    if (_state.value.revealed) stopUserTone()
  }

  /** Locks the answer in: scores the slider's position against the target and goes quiet. */
  fun check() {
    if (_state.value.revealed) return
    stopUserTone()
    _state.update { current ->
      current.copy(errorCents = 1200.0 * log2(current.currentHz / current.targetHz))
    }
  }

  fun nextRound() {
    if (!_state.value.revealed) return
    _state.update {
      it.copy(round = it.round + 1, targetHz = randomTargetHz(), errorCents = null)
    }
    playTargetThenResume()
  }

  override fun onCleared() {
    // The Notes channel is shared with the rest of the app: silence it and put the pitch wheel
    // back to its defaults on the way out.
    midiEngine.stopNote(PitchSlideCenterNote)
    midiEngine.setPitchBend(MidiChannel.Notes, 0.0)
    midiEngine.setPitchBendRange(MidiChannel.Notes, 2)
  }

  private fun bendFor(hz: Double): Double = 12.0 * log2(hz / PitchSlideCenterHz)

  /** Log-uniform target: every octave of the span is equally likely. */
  private fun randomTargetHz(random: Random = Random.Default): Double =
    exp(ln(PitchSlideMinHz) + (ln(PitchSlideMaxHz) - ln(PitchSlideMinHz)) * random.nextDouble())
}
