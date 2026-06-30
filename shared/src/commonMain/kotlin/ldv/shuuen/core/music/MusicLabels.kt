package ldv.shuuen.core.music

object MusicLabelDefaults {
  val NoteLetters: List<String> = listOf("C", "D", "E", "F", "G", "A", "B")

  /**
   * Custom note labels are stored as 7 naturals, 7 sharps, then 7 flats. Within each block the
   * letter order is C, D, E, F, G, A, B.
   */
  val NoteNames: List<String> =
    NoteLetters +
      NoteLetters.map { "$it♯" } +
      NoteLetters.map { "$it♭" }

  val DegreeNames: List<String> = Degree.chromaticOrder.map { it.label }
}

fun effectiveNoteNames(customNames: List<String>): List<String> =
  effectiveMusicLabels(customNames, MusicLabelDefaults.NoteNames)

fun effectiveDegreeNames(customNames: List<String>): List<String> =
  effectiveMusicLabels(customNames, MusicLabelDefaults.DegreeNames)

fun SpelledPitch.customLabel(customNoteNames: List<String>): String =
  effectiveNoteNames(customNoteNames)[labelIndex]

private val SpelledPitch.labelIndex: Int
  get() =
    when (accidental) {
      Accidental.Natural -> 0
      Accidental.Sharp -> 7
      Accidental.Flat -> 14
    } + letterIndex

private fun effectiveMusicLabels(
  customNames: List<String>,
  defaultNames: List<String>,
): List<String> =
  List(defaultNames.size) { index ->
    customNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: defaultNames[index]
  }
