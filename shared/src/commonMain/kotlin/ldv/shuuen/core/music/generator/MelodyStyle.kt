package ldv.shuuen.core.music.generator

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteValue

/** Which note ladder a figure's contour steps walk. */
@Serializable
enum class FigureLadder {
  /** Every note the level's scale and range allow. */
  Scale,

  /**
   * Only the tones of the context chord currently sounding — contours become arpeggios. Falls
   * back to [Scale] when no context chord (of at least two pitch classes) is active.
   */
  Chord,
}

/**
 * One rhythm building block: a short run of note values played back-to-back, with an optional
 * melodic contour. Melodies are generated figure by figure (not note by note), so units like an
 * eighth-note pair or a stepwise 3-2-1 run arrive whole.
 */
@Serializable
data class RhythmFigure(
  val values: List<NoteValue>,
  /**
   * Ladder-step moves between consecutive notes, one entry per gap ([values].size - 1 in total):
   * +1/-1 walks up/down the [ladder]'s notes, 0 repeats the note, and null lets the weighted
   * note picker choose freely. An empty list makes every gap a free choice.
   */
  val contour: List<Int?> = emptyList(),
  val ladder: FigureLadder = FigureLadder.Scale,
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
  /**
   * Extra multiplier for notes belonging to the context chord currently sounding underneath.
   * 1 (the default) ignores the context entirely; higher values pull the melody toward the
   * chord's tones. Has no effect when the level has no context or the chord is a bare drone.
   */
  val chordToneBoost: Double = 1.0,
) {
  init {
    require(
      intervalWeights.all { it > 0 } && degreeWeights.values.all { it > 0 } && chordToneBoost > 0
    ) {
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

/** What every pickable generation style exposes to the shared tier-grouped picker sheet. */
interface StylePreset {
  val id: String
  val name: String
  val description: String
  val tier: StyleTier
}

/**
 * How random melodies are shaped: the weighted rhythm figures notes arrive in, paired with the
 * note-picker weights that choose the pitches. Levels store the whole style rather than a preset
 * id, so saved levels keep their exact behavior as presets evolve and a future style editor can
 * produce fully custom instances.
 */
@Serializable
data class MelodyStyle(
  override val id: String,
  override val name: String,
  override val description: String,
  override val tier: StyleTier,
  val figures: List<WeightedFigure>,
  val noteWeights: NoteWeights,
) : StylePreset {
  init {
    require(figures.isNotEmpty()) { "A style needs at least one figure." }
  }

  /**
   * True when the style reacts to the sounding context chord — through a chord-tone boost or
   * chord-ladder figures — and so wants the context advanced before each question is generated.
   */
  val isContextAware: Boolean
    get() =
      noteWeights.chordToneBoost != 1.0 ||
        figures.any { it.figure.ladder == FigureLadder.Chord }
}
