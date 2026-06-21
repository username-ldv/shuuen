package ldv.shuuen.core.music

import kotlinx.serialization.Serializable

@Serializable
enum class Degree(val offset: Int, val label: String) {
  D1(0, "1"),
  D5(7, "5"),
  D2(2, "2"),
  D6(9, "6"),
  D3(4, "3"),
  D7(11, "7"),
  DS4(6, "♯4"),
  DF2(1, "♭2"),
  DF6(8, "♭6"),
  DF3(3, "♭3"),
  DF7(10, "♭7"),
  D4(5, "4");

  override fun toString(): String = label

  fun pitch(tonic: Pitch): Pitch = tonic + offset

  companion object {
    val chromaticOrder: List<Degree> =
      listOf(D1, DF2, D2, DF3, D3, D4, DS4, D5, DF6, D6, DF7, D7)

    fun fromName(name: String): Degree =
      entries.firstOrNull { it.label == name }
        ?: error("Unknown degree name: $name")

    fun fromOffset(offset: Int): Degree =
      entries.first { it.offset == offset.floorMod(12) }
  }
}

@Serializable
data class DegreeWithOctave(val degree: Degree, val octave: Int) {
  init {
    // todo: better check
    require(octave in 0..8) { "out of bounds" }
  }

  override fun toString(): String {
      return "${degree.label} · $octave"
  }
}