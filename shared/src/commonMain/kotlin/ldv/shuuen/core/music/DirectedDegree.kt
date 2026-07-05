package ldv.shuuen.core.music

import kotlinx.serialization.Serializable

/**
 * Direction of a melody step relative to the previous note: the nearest occurrence
 * of the degree above ([Up]) or below ([Down]) it. Mirrors how the player resolves
 * setup melodies (currently ascending-only via Note.next; Down is the planned extension).
 */
@Serializable
enum class DegreeDirection(val arrow: String) {
  Up("↑"), Down("↓");

  fun flipped(): DegreeDirection = if (this == Up) Down else Up
}

/** One setup-melody step: a degree plus the direction it is reached from the previous note. */
@Serializable
data class DirectedDegree(
  val degree: Degree,
  val direction: DegreeDirection = DegreeDirection.Up,
) {
  override fun toString(): String {
    return "${degree.label}${direction.arrow}"
  }
}

/** First step is the anchor and has no direction; later steps show their arrow. */
fun List<DirectedDegree>.stepLabels(): List<String> = mapIndexed { index, step ->
  if (index == 0) step.degree.label else step.toString()
}
