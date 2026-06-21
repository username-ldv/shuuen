package ldv.shuuen.features.training.common

import io.github.aakira.napier.Napier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.music.Chord
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.constructAscSetupMelodyFlow
import ldv.shuuen.core.music.toChord

private data class CurrentlyPlayingNode(
    val chord: Chord,
    val channel: MidiChannel,
    val startQuestionNumber: Int,
    val duration: ContextDuration,
)

private data class QuestionEvent(val currentQuestion: Int = 1, val newRoot: Pitch? = null)

class DegreeContextPlayer(
    val midiEngine: MidiEngine,
    val context: DegreeContext,
    startingRoot: Pitch,
    val endlessPreMelody: Duration = 2.seconds,
    val finitePreMelody: Duration = 1.seconds,
    val afterSetupMelody: Duration = 1.5.seconds,
) {
  private val questionEvent = MutableStateFlow(QuestionEvent())
  private val currentRoot = MutableStateFlow(startingRoot)
  private val _ready = MutableStateFlow(false)
  val ready = _ready.asStateFlow()
  private val _setupMelodyNotes = MutableStateFlow<Note?>(null)
  val setupMelodyNotes = _setupMelodyNotes.asStateFlow()
  private var setupMelody: RelativeMelody? = null
  private var currentlyPlaying: CurrentlyPlayingNode? = null
  var currentNodeCount: Int = 0
    private set

  suspend fun start() {
    val c = context
    require(c.nodes.isNotEmpty()) { "context can't be empty" }

    Napier.v { "in start, context $context" }

    try {
      // should collect indefinitely
      questionEvent.collect { questionEvent ->
        questionEvent.newRoot?.let { currentRoot.value = it }

        currentlyPlaying?.let { playing ->
          // if something plays already and the question duration is done, then stop
          val needRotation =
              when (val d = playing.duration) {
                is ContextDuration.Finite if
                    (questionEvent.currentQuestion - playing.startQuestionNumber >=
                        d.durationInQuestions)
                 -> true
                is ContextDuration.SameAsScaleRotation -> questionEvent.newRoot != null
                else -> false
              }
          Napier.v { "Need rotation: $needRotation" }
          if (needRotation) {
            stopCurrent(true)
            _ready.value = false
          } else {
            // else continue collecting current
            _ready.value = true
            return@collect
          }
        }
        // if nothing plays, play next
        playNode(c, questionEvent)
      }
    } finally {
      Napier.v { "Finally happened" }
      // is it needed?
      _ready.value = false
      stopCurrent(advance = false)
    }
  }

  suspend fun questionAdvanced(root: Pitch? = null) {
    _ready.value = false
    questionEvent.update {
      val q = it.currentQuestion + 1
      Napier.v { "Question in the player advanced to $q" }
      it.copy(currentQuestion = q, newRoot = root)
    }
    _ready.first { it }
  }

  fun isChangingNode(currentQuestion: Int, isNewRoot: Boolean): Boolean {
    return currentlyPlaying?.let { playing ->
      when (val d = playing.duration) {
        is ContextDuration.Finite -> currentQuestion - playing.startQuestionNumber >= d.durationInQuestions
        is ContextDuration.SameAsScaleRotation -> isNewRoot
        else -> false
      }
    } ?: false
  }

  suspend fun playSetupMelody() {
    val m = setupMelody ?: return
    var currentlyPlaying: Note? = null
    // todo: actually handle
    constructAscSetupMelodyFlow(
            currentRoot.value,
            m.let { melody ->
              listOf(melody.firstDegree.degree) + melody.extraDegrees.map { it.degree }
            },
        )
        .onEach { note ->
          withContext(NonCancellable) {
            currentlyPlaying?.let { midiEngine.stopNote(it) }
          }
        }
        .onCompletion {
          withContext(NonCancellable) {
            currentlyPlaying?.let { midiEngine.stopNote(it) }
          }
          _setupMelodyNotes.value = null
        }
        .collect { note ->
          midiEngine.playNote(note, MidiChannel.Notes)
          currentlyPlaying = note
          _setupMelodyNotes.value = note
        }
    delay(afterSetupMelody)
  }

  private fun stopCurrent(advance: Boolean) {
    val playing = currentlyPlaying ?: return
    midiEngine.stopChord(playing.chord, playing.channel)
    //    currentlyPlaying = null
    if (advance) currentNodeCount++
    Napier.v { "Stop current happened" }
  }

  private suspend fun playNode(c: DegreeContext, questionEvent: QuestionEvent) {
    Napier.v { "playNode call" }
    Napier.v {
      "While loop q: ${questionEvent.currentQuestion}, new root: ${questionEvent.newRoot}"
    }
    val node = c.nodes[currentNodeCount % c.nodes.size]
    val chord = node.toChord(currentRoot.value)
    Napier.v { "node's chord: $chord" }
    val channel =
        when (node.sustain) {
          is Sustain.Endless -> MidiChannel.Drone
          is Sustain.Finite -> MidiChannel.Cadence
        }
    midiEngine.playChord(chord, channel)
    //      val duration = when (val d = node.duration) {
    //            is ContextDuration.Endless -> null
    //            is ContextDuration.Finite -> d.durationInQuestions
    //            is ContextDuration.Immediate -> 0
    //            is ContextDuration.SameAsScaleRotation -> TODO("Not implemented yet.")
    //          }
    currentlyPlaying =
        CurrentlyPlayingNode(chord, channel, questionEvent.currentQuestion, node.duration)

    when (val sustain = node.sustain) {
      is Sustain.Endless -> {
        Napier.v { "Endless sustain node" }
        delay(endlessPreMelody)
        if (node.setupMelody != null) {
          setupMelody = node.setupMelody
          playSetupMelody()
        }
        _ready.value = true
        return
      }

      is Sustain.Finite -> {
        Napier.v { "Finite sustain node..." }
        delay(sustain.duration)
        stopCurrent(true)
        delay(finitePreMelody)
        if (node.setupMelody != null) {
          setupMelody = node.setupMelody
          playSetupMelody()
        }
        if (node.duration == ContextDuration.Immediate) {
          Napier.v { "continuing next..." }
          playNode(c, questionEvent)
        }
        _ready.value = true
        return
      }
    }
  }
}
