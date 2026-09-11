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

data class MusicLabelPreset(
  val name: String,
  val labels: List<String>,
)

object MusicLabelPresets {
  val NotePresets: List<MusicLabelPreset> =
    listOf(
      MusicLabelPreset("Default", MusicLabelDefaults.NoteNames),
      MusicLabelPreset(
        "Hybrid solfege",
        listOf(
          "Do", "Re", "Mi", "Fa", "So", "La", "Ti",
          "Di", "Ri", "Mya", "Fi", "Si", "Li", "Tya",
          "Du", "Ra", "Me", "Fe", "Se", "Le", "Te",
        ),
      ),
    )

  val DegreePresets: List<MusicLabelPreset> =
    listOf(
      MusicLabelPreset("Default", MusicLabelDefaults.DegreeNames),
      MusicLabelPreset(
        "Yoda",
        listOf("Yo", "Yu", "Ya", "Nu", "Na", "Sa", "Sha", "Ka", "Vu", "Va", "Ye", "Yi"),
      ),
    )
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
