package ldv.shuuen.core.music.generator

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteValue

/**
 * One rhythm building block: a short run of note values played back-to-back, with an optional
 * melodic contour. Melodies are generated figure by figure (not note by note), so units like an
 * eighth-note pair or a stepwise 3-2-1 run arrive whole.
 */
@Serializable
data class RhythmFigure(
  val values: List<NoteValue>,
  /**
   * Scale-step moves between consecutive notes, one entry per gap ([values].size - 1 in total):
   * +1/-1 walks up/down the notes the level allows, 0 repeats the note, and null lets the
   * weighted note picker choose freely. An empty list makes every gap a free choice.
   */
  val contour: List<Int?> = emptyList(),
) {
  init {
    require(values.isNotEmpty()) { "A figure needs at least one note." }
    require(contour.isEmpty() || contour.size == values.size - 1) {
      "Contour needs one entry per gap between the figure's notes."
    }
  }
}

@Serializable
data class WeightedFigure(val figure: RhythmFigure, val weight: Double) {
  init {
    require(weight > 0) { "Figure weights must be positive." }
  }
}

/**
 * Configuration of the weighted random note picker. A candidate note's weight is the product of
 * its melodic-interval weight (distance from the previous note) and its degree weight (how good
 * a landing spot the scale degree is), so awkward moves like a leap from 7 onto 4 stay possible
 * but rare while stepwise motion around stable degrees dominates.
 */
@Serializable
data class NoteWeights(
  /**
   * Weight per melodic interval in semitones, index 0 being a repeated note. Intervals past the
   * end of the list reuse the last entry; an empty list weighs every interval equally.
   */
  val intervalWeights: List<Double> = emptyList(),
  /** Multiplier per target scale degree; degrees not listed weigh 1. */
  val degreeWeights: Map<Degree, Double> = emptyMap(),
) {
  init {
    require(intervalWeights.all { it > 0 } && degreeWeights.values.all { it > 0 }) {
      "Note weights must be positive so every combination stays reachable."
    }
  }

  fun intervalWeight(semitones: Int): Double =
    when {
      intervalWeights.isEmpty() -> 1.0
      else -> intervalWeights.getOrElse(semitones) { intervalWeights.last() }
    }

  fun degreeWeight(degree: Degree): Double = degreeWeights[degree] ?: 1.0

  companion object {
    /** Every allowed note equally likely, regardless of the previous note. */
    val Uniform = NoteWeights()
  }
}

@Serializable
enum class StyleTier(val label: String) {
  Beginner("Beginner"),
  Intermediate("Intermediate"),
  Advanced("Advanced"),
}

/**
 * How random melodies are shaped: the weighted rhythm figures notes arrive in, paired with the
 * note-picker weights that choose the pitches. Levels store the whole style rather than a preset
 * id, so saved levels keep their exact behavior as presets evolve and a future style editor can
 * produce fully custom instances.
 */
@Serializable
data class MelodyStyle(
  val id: String,
  val name: String,
  val description: String,
  val tier: StyleTier,
  val figures: List<WeightedFigure>,
  val noteWeights: NoteWeights,
) {
  init {
    require(figures.isNotEmpty()) { "A style needs at least one figure." }
  }
}
