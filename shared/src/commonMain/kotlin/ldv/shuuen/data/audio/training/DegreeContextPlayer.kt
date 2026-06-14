package ldv.shuuen.data.audio.training

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import ldv.shuuen.domain.audio.engine.MidiEngine
import ldv.shuuen.domain.audio.midi.MidiChannel
import ldv.shuuen.domain.audio.music.Chord
import ldv.shuuen.domain.audio.music.ContextDuration
import ldv.shuuen.domain.audio.music.DegreeContext
import ldv.shuuen.domain.audio.music.Note
import ldv.shuuen.domain.audio.music.Pitch
import ldv.shuuen.domain.audio.music.Sustain
import ldv.shuuen.domain.audio.music.constructAscSetupMelodyFlow
import ldv.shuuen.domain.audio.music.toChord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private data class CurrentlyPlayingNode(
    val chord: Chord,
    val channel: MidiChannel,
    val startQuestionNumber: Int,
    val questionDuration: Int?,
)

class DegreeContextPlayer(
    val midiEngine: MidiEngine,
    val context: DegreeContext,
    startingRoot: Pitch,
    val endlessPreMelody: Duration = 2000.milliseconds,
    val afterSetupMelody: Duration = 1500.milliseconds,
) {
  private val currentQuestion = MutableStateFlow(0)
  private val currentRoot = MutableStateFlow(startingRoot)
  private val _ready = MutableStateFlow(false)
  val ready = _ready.asStateFlow()
  private val _setupMelodyNotes = MutableStateFlow<Note?>(null)
  val setupMelodyNotes = _setupMelodyNotes.asStateFlow()
  private var currentlyPlaying: CurrentlyPlayingNode? = null
  private var currentNodeCount = 0

  suspend fun start() {
    val c = context
    require(c.nodes.isNotEmpty()) { "context can't be empty" }

    Napier.v { "in start, context $context" }

    try {
      // should collect indefinitely
      currentQuestion.collect { questionNumber ->
        currentlyPlaying?.let { playing ->
          // if something plays already and the question duration is done, then stop
          if (
              playing.questionDuration != null &&
                  questionNumber - playing.startQuestionNumber >= playing.questionDuration
          ) {
            stopCurrent(true)
            _ready.value = false
          } else {
            // else continue collecting current
            return@collect
          }
        }
        // if nothing plays, play next
        playNode(c, questionNumber)
      }
    } finally {
      Napier.v { "Finally happened" }
      // is it needed?
      _ready.value = false
      stopCurrent(advance = false)
    }
  }

  fun questionAdvanced(root: Pitch?) {
    root?.let { currentRoot.value = root }
    currentQuestion.value++
  }

  private fun stopCurrent(advance: Boolean) {
    val playing = currentlyPlaying ?: return
    midiEngine.stopChord(playing.chord, playing.channel)
    currentlyPlaying = null
    if (advance) currentNodeCount++
    Napier.v { "Stop current happened" }
  }

  private suspend fun playNode(c: DegreeContext, questionNumber: Int) {
    while (currentlyPlaying == null) {
      Napier.v { "While loop q: $questionNumber" }
      val node = c.nodes[currentNodeCount % c.nodes.size]
      val chord = node.toChord(currentRoot.value)
      val channel =
          when (node.sustain) {
            is Sustain.Endless -> MidiChannel.Drone
            is Sustain.Finite -> MidiChannel.Cadence
          }
      midiEngine.playChord(chord, channel)
      val duration = when (val d = node.duration) {
        is ContextDuration.Endless -> null
        is ContextDuration.Finite -> d.durationInQuestions
        is ContextDuration.SameAsScaleRotation -> TODO("Not implemented yet.")
      }
      currentlyPlaying =
          CurrentlyPlayingNode(chord, channel, questionNumber, duration)

      when (val sustain = node.sustain) {
        is Sustain.Endless -> {
          delay(endlessPreMelody)
          if (node.setupMelody != null) {
            var currentlyPlaying: Note? = null
            // todo: actually handle
            constructAscSetupMelodyFlow(
                    currentRoot.value,
                    node.setupMelody.let { melody ->
                      listOf(melody.firstDegree.degree) + melody.extraDegrees.map { it.degree }
                    },
                )
                .onEach { note ->
                  currentlyPlaying?.let { midiEngine.stopNote(it) }
                }
                .onCompletion {
                  currentlyPlaying?.let { midiEngine.stopNote(it) }
                  _setupMelodyNotes.value = null
                }
                .collect { note ->
                  midiEngine.playNote(note, MidiChannel.Notes)
                  currentlyPlaying = note
                  _setupMelodyNotes.value = note
                }
            delay(afterSetupMelody)
          }
          _ready.value = true
          return
        }

        is Sustain.Finite -> {
          delay(sustain.duration)
          stopCurrent(true)
          // if durationInQuestion == null then just play next immediately
          if (duration == null) continue
          _ready.value = true
          return
        }
      }
    }
  }
}
