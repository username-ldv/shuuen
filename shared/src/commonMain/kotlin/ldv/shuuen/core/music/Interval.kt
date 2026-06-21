package ldv.shuuen.core.music

enum class IntervalType {
  m2,
  M2,
  m3,
  M3,
  P4,
  T,
  P5,
  m6,
  M6,
  m7,
  M7,
  U8;

  val semitones: Int get() = ordinal + 1
}

enum class IntervalDirection {
  Ascending,
  Descending,
}

data class Interval(
  val type: IntervalType,
  val notes: List<Note>,
) {
  init {
    require(notes.size == 2) { "An interval must contain exactly two notes." }
  }

  val pitches: List<Pitch> = notes.map { it.pitch }
  val direction: IntervalDirection =
    if (notes[0].midiIndex < notes[1].midiIndex) IntervalDirection.Ascending else IntervalDirection.Descending

  companion object {
    fun ascendingFrom(lowNote: Note, type: IntervalType): Interval =
      Interval(type = type, notes = listOf(lowNote, lowNote + type.semitones))

    fun ascendingTo(highNote: Note, type: IntervalType): Interval =
      Interval(type = type, notes = listOf(highNote - type.semitones, highNote))

    fun descendingFrom(highNote: Note, type: IntervalType): Interval =
      Interval(type = type, notes = listOf(highNote, highNote - type.semitones))

    fun descendingTo(lowNote: Note, type: IntervalType): Interval =
      Interval(type = type, notes = listOf(lowNote + type.semitones, lowNote))
  }
}
