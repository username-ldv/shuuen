package ldv.shuuen.features.training.melodies.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.ScaleType

/**
 * The labelled key of an imported MIDI melody: its tonic, the scale degrees the melody uses, and
 * how the key is spelled. The label is authored on the website against the catalog melody and
 * arrives inside the level definition (`config.key`); locally imported files have none.
 *
 * [scaleType] is Major or NaturalMinor only when [degrees] are exactly that scale; every other
 * degree set (Lydian, harmonic minor, ...) is [ScaleType.Custom] with the human [scaleName].
 */
@Serializable
data class MidiKey(
  val tonic: Pitch,
  val degrees: List<Degree>,
  val scaleType: ScaleType = ScaleType.Custom,
  val accidentalType: ScaleAccidentalType = ScaleAccidentalType.Sharps,
  val scaleName: String? = null,
) {
  init {
    require(degrees.isNotEmpty()) { "A MIDI key needs at least one degree." }
    require(degrees.distinct().size == degrees.size) { "A MIDI key cannot repeat degrees." }
  }

  /** The key after the file is transposed by [semitones]; the degree set is unchanged. */
  fun transposed(semitones: Int): MidiKey = copy(tonic = tonic + semitones)

  /** "E♭ major", "A natural minor", "F lydian", or "F custom" when no name was given. */
  fun displayName(): String {
    val tonicName =
      Scale.appropriatePitchName(tonic, tonic, scaleType, accidentalType)
    val scale =
      when (scaleType) {
        ScaleType.Major -> "major"
        ScaleType.NaturalMinor -> "natural minor"
        ScaleType.Chromatic -> "chromatic"
        ScaleType.Custom -> scaleName?.takeIf { it.isNotBlank() }?.lowercase() ?: "custom"
      }
    return "$tonicName $scale"
  }

  companion object {
    /** Classifies a degree set the same way the backend does. */
    fun scaleTypeFor(degrees: Collection<Degree>): ScaleType {
      val set = degrees.toSet()
      return when (set) {
        Scale.major(Pitch.C).degrees.toSet() -> ScaleType.Major
        Scale.naturalMinor(Pitch.C).degrees.toSet() -> ScaleType.NaturalMinor
        Degree.chromaticOrder.toSet() -> ScaleType.Chromatic
        else -> ScaleType.Custom
      }
    }
  }
}
