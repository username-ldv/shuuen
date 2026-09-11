package ldv.shuuen.core.music.generator

import ldv.shuuen.core.music.generator.ChordFigure.FreePick
import ldv.shuuen.core.music.generator.ChordFigure.Stacked

/** The predefined chord styles offered in the chords setup, ordered easiest first. */
object ChordStyles {
  // Shapes named for their meaning over a full seven-note scale; on a reduced scale they still
  // stack the same ladder distances, staying "on scale" by construction.
  private val triad = Stacked(listOf(0, 2, 4))
  private val triadInv1 = Stacked(listOf(0, 2, 5))
  private val triadInv2 = Stacked(listOf(0, 3, 5))
  private val openTriad = Stacked(listOf(0, 4, 9))
  private val seventh = Stacked(listOf(0, 2, 4, 6))
  private val seventhInv1 = Stacked(listOf(0, 2, 4, 5))
  private val seventhInv2 = Stacked(listOf(0, 2, 3, 5))
  private val seventhInv3 = Stacked(listOf(0, 1, 3, 5))

  private fun ChordFigure.weighing(weight: Double) = WeightedChordFigure(this, weight)

  val RootTriads =
    ChordStyle(
      id = "root-triads",
      name = "Root triads",
      description = "Diatonic triads stacked from the scale, always in root position.",
      tier = StyleTier.Beginner,
      figures = listOf(triad.weighing(1.0)),
    )

  val TriadsAndInversions =
    ChordStyle(
      id = "triads-and-inversions",
      name = "Triads & inversions",
      description = "Mostly root-position triads; their inversions slip in now and then.",
      tier = StyleTier.Beginner,
      figures =
        listOf(
          triad.weighing(4.0),
          triadInv1.weighing(1.5),
          triadInv2.weighing(1.5),
        ),
    )

  val AddTheSeventh =
    ChordStyle(
      id = "add-the-seventh",
      name = "Add the seventh",
      description = "Triads and inversions with the occasional seventh chord on top.",
      tier = StyleTier.Intermediate,
      figures =
        listOf(
          triad.weighing(3.0),
          triadInv1.weighing(1.0),
          triadInv2.weighing(1.0),
          seventh.weighing(1.5),
        ),
    )

  val LeaningDiatonic =
    ChordStyle(
      id = "leaning-diatonic",
      name = "Leaning diatonic",
      description =
        "Everything can appear, weighted by familiarity: root triads first, then inversions, sevenths, and rarely a fully random pick.",
      tier = StyleTier.Intermediate,
      figures =
        listOf(
          triad.weighing(4.0),
          triadInv1.weighing(1.5),
          triadInv2.weighing(1.5),
          seventh.weighing(1.0),
          seventhInv1.weighing(0.4),
          seventhInv2.weighing(0.4),
          seventhInv3.weighing(0.4),
          FreePick.weighing(0.5),
        ),
    )

  val SeventhFlavors =
    ChordStyle(
      id = "seventh-flavors",
      name = "Seventh flavors",
      description = "Seventh chords in all inversions, spread voicings, and plain triads to rest on.",
      tier = StyleTier.Advanced,
      figures =
        listOf(
          seventh.weighing(2.0),
          seventhInv1.weighing(1.0),
          seventhInv2.weighing(1.0),
          seventhInv3.weighing(0.8),
          openTriad.weighing(1.0),
          triad.weighing(1.0),
        ),
    )

  val AnythingGoes =
    ChordStyle(
      id = "anything-goes",
      name = "Anything goes",
      description = "Fully random note stacks from the scale — the original drill.",
      tier = StyleTier.Advanced,
      figures = listOf(FreePick.weighing(1.0)),
    )

  /** Matches the behavior levels had before styles existed; also the default for new levels. */
  val Default = AnythingGoes

  val presets: List<ChordStyle> =
    listOf(
      RootTriads,
      TriadsAndInversions,
      AddTheSeventh,
      LeaningDiatonic,
      SeventhFlavors,
      AnythingGoes,
    )
}
