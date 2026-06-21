package ldv.shuuen.features.training.melodies.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ldv.shuuen.core.audio.engine.MelodyNote
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.engine.MidiFilePlaybackOptions
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.features.training.melodies.domain.MelodiesSession

data class IncorrectMelodyAnswer(
  val noteIndex: Int,
  val expectedPitch: Pitch,
  val guessedPitch: Pitch,
)

data class MelodiesPlayState(
  val title: String = "Melody",
  val isLoading: Boolean = true,
  val error: String? = null,
  val notes: List<MelodyNote> = emptyList(),
  val lengthTicks: Long = 0L,
  val lengthSeconds: Double = 0.0,
  val positionTicks: Long = 0L,
  val positionSeconds: Double = 0.0,
  val isPlaying: Boolean = false,
  val answerIndex: Int = 0,
  val answeredPitches: List<Pitch> = emptyList(),
  val correctAnswers: Int = 0,
  val incorrectAnswers: List<IncorrectMelodyAnswer> = emptyList(),
) {
  /** Index of the most recent note-on at or before the current position, or -1 before the first. */
  val playbackNoteIndex: Int
    get() = notes.indexOfLast { it.tick <= positionTicks }

  val progress: Float
    get() = if (lengthTicks > 0) (positionTicks.toFloat() / lengthTicks).coerceIn(0f, 1f) else 0f

  val quizProgress: Float
    get() = if (notes.isNotEmpty()) (answerIndex.toFloat() / notes.size).coerceIn(0f, 1f) else 0f

  val isQuizComplete: Boolean
    get() = notes.isNotEmpty() && answerIndex >= notes.size
}

private val pollInterval = 50.milliseconds
private const val SeekSeconds = 5.0

class MelodiesPlayScreenViewModel(
  session: MelodiesSession,
  private val midiEngine: MidiEngine,
  private val player: MidiFilePlayer,
) : ViewModel() {
  private val _state = MutableStateFlow(MelodiesPlayState())
  val state = _state.asStateFlow()

  private var pollJob: Job? = null

  init {
    val level = session.current.value
    viewModelScope.launch {
      when (val status = midiEngine.initialize()) {
        MidiEngineStatus.Ready -> Napier.v { "MIDI engine ready for melodies player" }
        is MidiEngineStatus.Failed -> {
          _state.update { it.copy(isLoading = false, error = "Audio init failed: ${status.message}") }
          return@launch
        }
      }
      if (level == null) {
        _state.update { it.copy(isLoading = false, error = "No melody to play.") }
        return@launch
      }

      val loaded =
        runCatching {
          player.load(
            level.midiBytes,
            MidiFilePlaybackOptions(useOriginalVelocities = level.useOriginalVelocities),
          )
        }
          .getOrElse { throwable ->
            Napier.w(throwable) { "Failed to load melody" }
            _state.update { it.copy(isLoading = false, error = "Couldn't load the MIDI file.") }
            return@launch
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
        return@launch
      }
      _state.update {
        it.copy(
          title = level.name,
          isLoading = false,
          notes = loaded.notes,
          lengthTicks = loaded.lengthTicks,
          lengthSeconds = loaded.lengthSeconds,
        )
      }
      // Play through at natural tempo by default.
      player.play()
      refreshTransportState()
      startPolling()
    }
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

  fun userGuessed(pitch: Pitch): Boolean? {
    val current = _state.value
    val answerNote = current.notes.getOrNull(current.answerIndex) ?: return null
    val isCorrect = answerNote.note.pitch == pitch

    _state.update { state ->
      val note = state.notes.getOrNull(state.answerIndex) ?: return@update state
      if (note.note.pitch == pitch) {
        val alreadyMissed = state.incorrectAnswers.any { it.noteIndex == state.answerIndex }
        state.copy(
          answerIndex = state.answerIndex + 1,
          answeredPitches = state.answeredPitches + pitch,
          correctAnswers = state.correctAnswers + if (alreadyMissed) 0 else 1,
        )
      } else {
        val duplicate = state.incorrectAnswers.any { it.noteIndex == state.answerIndex }
        if (duplicate) {
          state
        } else {
          state.copy(
            incorrectAnswers =
              state.incorrectAnswers +
                IncorrectMelodyAnswer(
                  noteIndex = state.answerIndex,
                  expectedPitch = note.note.pitch,
                  guessedPitch = pitch,
                ),
          )
        }
      }
    }

    return isCorrect
  }

  fun togglePlayPause() {
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

  fun seekForward() {
    player.seekBySeconds(SeekSeconds)
    refreshTransportState()
  }

  fun seekBackward() {
    player.seekBySeconds(-SeekSeconds)
    refreshTransportState()
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

  override fun onCleared() {
    pollJob?.cancel()
    player.release()
  }
}
