package ldv.shuuen.core.music

import kotlinx.serialization.Serializable

/** A rhythm value, measured in quarter-note beats. */
@Serializable
enum class NoteValue(val quarters: Double) {
  Whole(4.0),
  Half(2.0),
  DottedQuarter(1.5),
  Quarter(1.0),
  Eighth(0.5),
  Sixteenth(0.25),
}
