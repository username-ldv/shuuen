package ldv.shuuen.core.music

enum class ScaleType(val scaleName: String) {
  Major("Major"), NaturalMinor("Natural Minor"), Chromatic("Chromatic"), Custom("Custom");

  override fun toString(): String {
    return this.scaleName
  }

  companion object {
    fun fromName(s: String): ScaleType? {
      return entries.firstOrNull { it.scaleName == s }
    }
  }
}

enum class ScaleAccidentalType {
  Sharps, Flats,
}

private val sharpMajorKeys = List(6) { index -> Pitch.C + 7 * (index + 1) }
private val flatMajorKeys = List(6) { index -> Pitch.C - 7 * (index + 1) }
private val sharpMinorKeys = sharpMajorKeys.map { it - 3 }
private val flatMinorKeys = flatMajorKeys.map { it - 3 }

data class Scale(
  val pitches: List<Pitch>,
  val formula: List<Int>,
  val type: ScaleType,
) {
  val root: Pitch = pitches.first()

  fun notes(startingOctave: Int): List<Note> {
    val rootNote = Note(root, startingOctave)
    return formula.runningReduce { acc, step -> acc + step }.map { rootNote + it }
  }

  val degrees: List<Degree>
    get() {
      return formula.runningReduce { acc, step -> acc + step }.map { Degree.fromOffset(it) }
    }

  fun appropriatePitchNames(accidentalResolutionType: ScaleAccidentalType = ScaleAccidentalType.Sharps): List<String> {
    return pitches.map { pitch ->
      appropriatePitchName(
        root,
        pitch,
        type,
        accidentalResolutionType
      )
    }
  }

  companion object {
    fun chromatic(root: Pitch): Scale =
      // Scale configuration stores pitch classes, not the repeated root at the octave.
      fromFormula(root, listOf(0) + List(11) { 1 }, ScaleType.Chromatic)

    fun major(root: Pitch): Scale = fromFormula(root, listOf(0, 2, 2, 1, 2, 2, 2), ScaleType.Major)

    fun naturalMinor(root: Pitch): Scale =
      fromFormula(root, listOf(0, 2, 1, 2, 2, 1, 2), ScaleType.NaturalMinor)

    fun custom(root: Pitch, formula: List<Int>): Scale =
      fromFormula(root, formula, ScaleType.Custom)

    fun fromScaleType(root: Pitch, scaleType: ScaleType, formula: List<Int>? = null): Scale {
      return when (scaleType) {
        ScaleType.Major -> major(root)
        ScaleType.NaturalMinor -> naturalMinor(root)
        ScaleType.Chromatic -> chromatic(root)
        ScaleType.Custom -> {
          require(formula != null) { "with custom scale, formula can't be null" }
          custom(root, formula)
        }
      }
    }

    fun appropriatePitchName(
      root: Pitch,
      pitch: Pitch,
      type: ScaleType,
      accidentalResolutionType: ScaleAccidentalType = ScaleAccidentalType.Sharps,
    ): String {
      val useSharps = when (type) {
        ScaleType.Major -> if (accidentalResolutionType == ScaleAccidentalType.Sharps) (root in sharpMajorKeys) else (root !in flatMajorKeys)
        ScaleType.NaturalMinor -> if (accidentalResolutionType == ScaleAccidentalType.Sharps) (root in sharpMinorKeys) else (root !in flatMinorKeys)
        ScaleType.Chromatic, ScaleType.Custom -> accidentalResolutionType == ScaleAccidentalType.Sharps
      }
      val appropriateName = if (useSharps) pitch.toString() else pitch.toFlatString()
//      if (pitch == Pitch.ASharp) Napier.v { "Appropriate pitch name root: $root, name: $appropriateName, type: $type, useSharps: $useSharps" }
      return appropriateName
    }

    private fun fromFormula(
      root: Pitch, formula: List<Int>, type: ScaleType = ScaleType.Custom
    ): Scale {
      require(formula.isNotEmpty()) { "Scale formula must not be empty." }
      val pitches = formula.runningReduce { acc, step -> acc + step }.map { root + it }
      return Scale(pitches = pitches, formula = formula, type = type)
    }
  }
}
