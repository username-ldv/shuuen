package ldv.shuuen.features.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiEngineStatus
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.constructSetupMelodyFlow

private const val PreviewTempo = 90

class ContextViewModel(
  private val midiEngine: MidiEngine,
) : ViewModel() {
  private var audioReady = false
  private var previewJob: Job? = null
  private var _playingMelody = MutableStateFlow(false)
  val playingMelody = _playingMelody.asStateFlow()

  init {
    viewModelScope.launch {
      audioReady = midiEngine.initialize() == MidiEngineStatus.Ready
    }
  }

  fun previewSetupMelody(melody: RelativeMelody) {
    previewJob?.cancel()
    previewJob =
      viewModelScope.launch {
        if (!audioReady) return@launch

        midiEngine.stopAll(MidiChannel.Notes)
        var currentNote: Note? = null
        try {
          _playingMelody.value = true
          constructSetupMelodyFlow(Pitch.random(), melody, PreviewTempo).collect { note ->
            currentNote?.let { midiEngine.stopNote(it, MidiChannel.Notes) }
            currentNote = note
            midiEngine.playNote(currentNote, MidiChannel.Notes)
          }
        } finally {
          withContext(NonCancellable) {
            currentNote?.let { midiEngine.stopNote(it, MidiChannel.Notes) }
            _playingMelody.value = false
          }
        }
      }
  }

  override fun onCleared() {
    previewJob?.cancel()
    midiEngine.stopAll(MidiChannel.Notes)
  }
}
