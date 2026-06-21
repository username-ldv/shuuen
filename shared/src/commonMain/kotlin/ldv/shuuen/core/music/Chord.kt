package ldv.shuuen.core.music

enum class ChordType(val formula: List<Int>) {
  Major(listOf(0, 4, 7)),
  Minor(listOf(0, 3, 7)),
}

data class Chord(
  val notes: List<Note>,
  val type: ChordType? = null,
) {
  init {
    require(notes.isNotEmpty()) { "Chord must contain at least one note." }
  }

  override fun toString(): String = notes.joinToString(", ")

  companion object {
    fun major(from: Note, inversion: Int = 0): Chord =
      fromFormula(from, ChordType.Major, inversion)

    fun minor(from: Note, inversion: Int = 0): Chord =
      fromFormula(from, ChordType.Minor, inversion)

    private fun fromFormula(from: Note, type: ChordType, inversion: Int): Chord {
      require(inversion in 0 until type.formula.size) {
        "Inversion must be in 0 until ${type.formula.size}, was $inversion."
      }
      val notes = type.formula.map { from + it }.toMutableList()
      repeat(inversion) {
        notes[it] = notes[it] + 12
      }
      return Chord(notes.sortedBy { it.midiIndex }, type)
    }
  }
}

fun List<Note>.chord(): Chord = Chord(this)
