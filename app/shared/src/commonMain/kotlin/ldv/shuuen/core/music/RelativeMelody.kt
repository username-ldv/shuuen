package ldv.shuuen.core.music

import kotlinx.serialization.Serializable


@Serializable
data class RelativeMelody(
  val firstDegree: DegreeWithOctave, val extraDegrees: List<DirectedDegree> = listOf()
)

fun RelativeMelody.stepLabels(): List<String> {
  return listOf("${this.firstDegree.degree.label} · ${this.firstDegree.octave}") + this.extraDegrees.map { "${it.degree.label}${it.direction.arrow}" }
}