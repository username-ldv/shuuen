package ldv.shuuen.core.music

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class DegreeContext(
    val id: String,
    val source: ContextSource,
    val nodes: List<DegreeContextNode>,
    val name: String? = null,
)

enum class SetupMelodyRepeat {
  // Once at the start / once every key change
  Once,
  // Every time this node comes (if the duration allows it to repeat the sequence within the same
  // key)
  EveryTime,
}

@Serializable data class SetupMelody(val melody: RelativeMelody, val repeat: SetupMelodyRepeat)

@Serializable
data class DegreeContextNode(
    val firstDegree: DegreeWithOctave,
    val extraDegrees: List<Degree>,
    val sustain: Sustain,
    val duration: ContextDuration,
    val setupMelody: SetupMelody?,
)

fun DegreeContextNode.toChord(root: Pitch): Chord {
  val first = Note(this.firstDegree.degree.pitch(root), this.firstDegree.octave)
  val notes =
      listOf(first) +
          this.extraDegrees.fold(
              listOf<Note>(),
              { acc, degree ->
                val note = (acc.lastOrNull() ?: first).next(degree.pitch(root))
                acc + note
              },
          )
  return notes.chord()
}

@Serializable
sealed interface Sustain {
  @Serializable data object Endless : Sustain

  @Serializable data class Finite(val duration: Duration) : Sustain
}

@Serializable
sealed interface ContextDuration {
  @Serializable
  data class Finite(val durationInQuestions: Int) : ContextDuration {
    init {
      require(durationInQuestions > 0) {
        "can't be 0 or less, map to ContextDuration.Endless instead"
      }
    }
  }

  @Serializable data object Immediate : ContextDuration

  @Serializable data object Endless : ContextDuration

  @Serializable data object SameAsScaleRotation : ContextDuration
}

val defaultContext =
    DegreeContext(
        id = "default",
        source = ContextSource.BuiltIn,
        nodes =
            listOf(
                DegreeContextNode(
                    firstDegree = DegreeWithOctave(Degree.D1, 2),
                    extraDegrees = listOf(),
                    sustain = Sustain.Endless,
                    duration = ContextDuration.Endless,
                    setupMelody =
                        SetupMelody(
                            melody =
                                RelativeMelody(
                                    firstDegree = DegreeWithOctave(Degree.D1, 4),
                                    extraDegrees =
                                        listOf(
                                            DirectedDegree(Degree.D3, DegreeDirection.Up),
                                            DirectedDegree(Degree.D5, DegreeDirection.Up),
                                            DirectedDegree(
                                                Degree.D1,
                                                DegreeDirection.Up,
                                            ),
                                        ),
                                ),
                            repeat = SetupMelodyRepeat.Once,
                        ),
                )
            ),
    )
