package ldv.shuuen.features.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import ldv.shuuen.core.music.Timing
import ldv.shuuen.core.music.constructSetupMelodyFlow
import ldv.shuuen.core.music.toChord

private const val PreviewTempo = 90
private val PreviewRoot = Pitch.C
private val PreviewGap = Timing(PreviewTempo).eighth()
private val EndlessStepPreviewDuration = Timing(PreviewTempo).half()

private data class PlayingNodePreview(
    val number: Int,
    val chord: Chord,
    val channel: MidiChannel,
)

class ContextViewModel(
    private val midiEngine: MidiEngine,
) : ViewModel() {
  private var audioReady = false
  private var setupMelodyPreviewJob: Job? = null
  private var nodePreviewJob: Job? = null
  private var fullSequencePreviewJob: Job? = null
  private var activeNodePreview: PlayingNodePreview? = null
  private val _playingMelody = MutableStateFlow(false)
  val playingMelody = _playingMelody.asStateFlow()
  private val _playingNodeNumber = MutableStateFlow<Int?>(null)
  val playingNodeNumber = _playingNodeNumber.asStateFlow()
  private val _playingFullSequence = MutableStateFlow(false)
  val playingFullSequence = _playingFullSequence.asStateFlow()

  init {
    viewModelScope.launch {
      audioReady = midiEngine.initialize() == MidiEngineStatus.Ready
    }
  }

  fun previewSetupMelody(melody: RelativeMelody) {
    val previousJob = setupMelodyPreviewJob
    val previousSequenceJob = fullSequencePreviewJob
    setupMelodyPreviewJob = viewModelScope.launch {
      previousSequenceJob?.cancelAndJoin()
      previousJob?.cancelAndJoin()
      if (!ensureAudioReady()) return@launch

      midiEngine.stopAll(MidiChannel.Notes)
      playSetupMelodyNotes(melody)
    }
  }

  fun toggleNodePreview(number: Int, node: DegreeContextNode) {
    val current = activeNodePreview
    if (fullSequencePreviewJob?.isActive != true && current?.number == number) {
      stopNodePreview()
      return
    }

    val previousJob = nodePreviewJob
    val previousSequenceJob = fullSequencePreviewJob
    nodePreviewJob = viewModelScope.launch {
      previousSequenceJob?.cancelAndJoin()
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

  fun toggleFullSequencePreview(nodes: List<DegreeContextNode>) {
    if (fullSequencePreviewJob?.isActive == true) {
      stopFullSequencePreview()
      return
    }

    val previousNodeJob = nodePreviewJob
    val previousMelodyJob = setupMelodyPreviewJob
    fullSequencePreviewJob = viewModelScope.launch {
      previousNodeJob?.cancelAndJoin()
      previousMelodyJob?.cancelAndJoin()
      stopActiveNodePreview()
      midiEngine.stopAll(MidiChannel.Notes)
      if (!ensureAudioReady()) return@launch

      try {
        _playingFullSequence.value = true
        playFullSequence(nodes)
      } finally {
        withContext(NonCancellable) {
          stopActiveNodePreview()
          midiEngine.stopAll(MidiChannel.Notes)
          _playingMelody.value = false
          _playingFullSequence.value = false
        }
      }
    }
  }

  fun stopNodePreview() {
    nodePreviewJob?.cancel()
    nodePreviewJob = null
    stopActiveNodePreview()
  }

  fun stopFullSequencePreview() {
    fullSequencePreviewJob?.cancel()
    fullSequencePreviewJob = null
    stopActiveNodePreview()
    midiEngine.stopAll(MidiChannel.Notes)
    _playingMelody.value = false
    _playingFullSequence.value = false
  }

  fun stopPreviewsUsingSequenceNodes() {
    stopFullSequencePreview()
    stopNodePreview()
  }

  private suspend fun playFullSequence(nodes: List<DegreeContextNode>) {
    nodes.forEachIndexed { index, node ->
      val preview = playNodeChord(number = index + 1, node = node)

      when (val sustain = node.sustain) {
        is Sustain.Finite -> {
          delay(sustain.duration)
          stopNodePreview(preview)
          node.setupMelody?.let {
            playSetupMelodyNotes(it.melody)
            delay(PreviewGap)
          }
        }

        Sustain.Endless -> {
          if (node.setupMelody == null) {
            delay(EndlessStepPreviewDuration)
          } else {
            delay(PreviewGap)
            playSetupMelodyNotes(node.setupMelody.melody)
          }
          if (index == nodes.lastIndex) {
            awaitCancellation()
          } else {
            delay(PreviewGap)
            stopNodePreview(preview)
          }
        }
      }
    }
  }

  private fun playNodeChord(number: Int, node: DegreeContextNode): PlayingNodePreview {
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
        )

    activeNodePreview = preview
    _playingNodeNumber.value = number
    midiEngine.playChord(preview.chord, preview.channel)
    return preview
  }

  private suspend fun playSetupMelodyNotes(melody: RelativeMelody) {
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
    fullSequencePreviewJob?.cancel()
    midiEngine.stopAll(MidiChannel.Notes)
    stopActiveNodePreview()
  }
}
