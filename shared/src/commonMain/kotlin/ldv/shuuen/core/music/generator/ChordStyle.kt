package ldv.shuuen.core.music.generator

import kotlinx.serialization.Serializable

/** One way to build a question's chord. */
@Serializable
sealed interface ChordFigure {
  /**
   * Uniformly random distinct notes, sized by the level's chord-size range — the original,
   * fully random drill.
   */
  @Serializable
  data object FreePick : ChordFigure

  /**
   * A stacked shape: offsets in ladder steps from a randomly chosen bass note, where the ladder
   * is every note the level's scale and range allow. Over a full seven-note scale, [0, 2, 4]
   * builds diatonic root-position triads, [0, 2, 5] and [0, 3, 5] their inversions,
   * [0, 2, 4, 6] seventh chords, and so on for any custom shape.
   */
  @Serializable
  data class Stacked(val ladderSteps: List<Int>) : ChordFigure {
    init {
      require(ladderSteps.size >= 2) { "A stacked shape needs at least two notes." }
      require(ladderSteps.first() == 0) { "Ladder steps are offsets from the bass; start at 0." }
      require(ladderSteps.zipWithNext().all { (a, b) -> a < b }) {
        "Ladder steps must strictly increase."
      }
    }
  }
}

@Serializable
data class WeightedChordFigure(val figure: ChordFigure, val weight: Double) {
  init {
    require(weight > 0) { "Chord figure weights must be positive." }
  }
}

/**
 * How random chords are shaped: weighted chord figures, from strictly diatonic stacks to free
 * random picks. Levels store the whole style rather than a preset id, so saved levels keep
 * their exact behavior as presets evolve and a future style editor can produce custom instances.
 *
 * Figures whose size falls outside the level's chord-size range are skipped at generation time;
 * a style with no usable figure falls back to the free pick.
 */
@Serializable
data class ChordStyle(
  override val id: String,
  override val name: String,
  override val description: String,
  override val tier: StyleTier,
  val figures: List<WeightedChordFigure>,
) : StylePreset {
  init {
    require(figures.isNotEmpty()) { "A style needs at least one figure." }
  }
}
