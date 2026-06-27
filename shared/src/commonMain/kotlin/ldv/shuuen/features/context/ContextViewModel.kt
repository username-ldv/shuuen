package ldv.shuuen.features.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.constructSetupMelodyFlow
import ldv.shuuen.core.music.toChord

private const val PreviewTempo = 90
private val PreviewRoot = Pitch.C

private data class PlayingNodePreview(
  val number: Int,
  val chord: Chord,
  val channel: MidiChannel,
  val sustained: Boolean,
)

class ContextViewModel(
  private val midiEngine: MidiEngine,
) : ViewModel() {
  private var audioReady = false
  private var setupMelodyPreviewJob: Job? = null
  private var nodePreviewJob: Job? = null
  private var activeNodePreview: PlayingNodePreview? = null
  private val _playingMelody = MutableStateFlow(false)
  val playingMelody = _playingMelody.asStateFlow()
  private val _playingNodeNumber = MutableStateFlow<Int?>(null)
  val playingNodeNumber = _playingNodeNumber.asStateFlow()

  init {
    viewModelScope.launch {
      audioReady = midiEngine.initialize() == MidiEngineStatus.Ready
    }
  }

  fun previewSetupMelody(melody: RelativeMelody) {
    val previousJob = setupMelodyPreviewJob
    setupMelodyPreviewJob =
      viewModelScope.launch {
        previousJob?.cancelAndJoin()
        if (!ensureAudioReady()) return@launch

        midiEngine.stopAll(MidiChannel.Notes)
        var currentNote: Note? = null
        try {
          _playingMelody.value = true
          constructSetupMelodyFlow(PreviewRoot, melody, PreviewTempo).collect { note ->
            currentNote?.let { midiEngine.stopNote(it, MidiChannel.Notes) }
            currentNote = note
            midiEngine.playNote(note, MidiChannel.Notes)
          }
        } finally {
          withContext(NonCancellable) {
            currentNote?.let { midiEngine.stopNote(it, MidiChannel.Notes) }
            _playingMelody.value = false
          }
        }
      }
  }

  fun toggleNodePreview(number: Int, node: DegreeContextNode) {
    val current = activeNodePreview
    if (current?.number == number) {
      stopNodePreview()
      return
    }

    val previousJob = nodePreviewJob
    nodePreviewJob =
      viewModelScope.launch {
        previousJob?.cancelAndJoin()
        stopActiveNodePreview()
        if (!ensureAudioReady()) return@launch

        val channel =
          when (node.sustain) {
            is Sustain.Endless -> MidiChannel.Drone
            is Sustain.Finite -> MidiChannel.Cadence
          }
        val preview =
          PlayingNodePreview(
            number = number,
            chord = node.toChord(PreviewRoot),
            channel = channel,
            sustained = node.sustain is Sustain.Endless,
          )

        activeNodePreview = preview
        _playingNodeNumber.value = number
        midiEngine.playChord(preview.chord, preview.channel)
        try {
          when (val sustain = node.sustain) {
            is Sustain.Endless -> awaitCancellation()
            is Sustain.Finite -> delay(sustain.duration)
          }
        } finally {
          withContext(NonCancellable) {
            stopNodePreview(preview)
          }
        }
      }
  }

  fun stopNodePreview() {
    nodePreviewJob?.cancel()
    nodePreviewJob = null
    stopActiveNodePreview()
  }

  private suspend fun ensureAudioReady(): Boolean {
    if (audioReady) return true

    audioReady = midiEngine.initialize() == MidiEngineStatus.Ready
    return audioReady
  }

  private fun stopActiveNodePreview() {
    activeNodePreview?.let(::stopNodePreview)
  }

  private fun stopNodePreview(preview: PlayingNodePreview) {
    midiEngine.stopChord(preview.chord, preview.channel)
    if (activeNodePreview == preview) {
      activeNodePreview = null
      _playingNodeNumber.value = null
    }
  }

  override fun onCleared() {
    setupMelodyPreviewJob?.cancel()
    nodePreviewJob?.cancel()
    midiEngine.stopAll(MidiChannel.Notes)
    stopActiveNodePreview()
  }
}
