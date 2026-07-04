package ldv.shuuen.core.music.generator

import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.core.music.NoteValue.DottedQuarter
import ldv.shuuen.core.music.NoteValue.Eighth
import ldv.shuuen.core.music.NoteValue.Half
import ldv.shuuen.core.music.NoteValue.Quarter
import ldv.shuuen.core.music.NoteValue.Sixteenth

/** The predefined melody styles offered in the melodies setup, ordered easiest first. */
object MelodyStyles {
  /**
   * Singable profile shared by the musical presets: seconds and small consonant leaps dominate,
   * tritones and sevenths are rare, and stable degrees pull more landings than tendency tones.
   * 3/♭3 and 6/♭6 carry the same weight so the profile fits major and minor scales alike;
   * out-of-scale degrees never compete anyway because only allowed notes are candidates.
   */
  private val Singable =
    NoteWeights(
      intervalWeights =
        listOf(
          2.0, // repeated note
          5.0, 5.0, // seconds
          3.0, 3.0, // thirds
          1.6, // fourth
          0.35, // tritone
          1.6, // fifth
          0.7, 0.7, // sixths
          0.3, 0.3, // sevenths
          0.8, // octave
          0.15, // anything wider
        ),
      degreeWeights =
        mapOf(
          Degree.D1 to 2.6,
          Degree.D5 to 2.2,
          Degree.D3 to 2.0,
          Degree.DF3 to 2.0,
          Degree.D2 to 1.5,
          Degree.D6 to 1.2,
          Degree.DF6 to 1.2,
          Degree.D4 to 1.1,
          Degree.DF7 to 1.0,
          Degree.D7 to 0.9,
          Degree.DF2 to 0.4,
          Degree.DS4 to 0.4,
        ),
    )

  private fun figure(
    vararg values: NoteValue,
    contour: List<Int?> = emptyList(),
    ladder: FigureLadder = FigureLadder.Scale,
  ) = RhythmFigure(values.toList(), contour, ladder)

  private fun RhythmFigure.weighing(weight: Double) = WeightedFigure(this, weight)

  private val quarter = figure(Quarter)
  private val half = figure(Half)
  private val eighthPairUp = figure(Eighth, Eighth, contour = listOf(1))
  private val eighthPairDown = figure(Eighth, Eighth, contour = listOf(-1))
  private val eighthPairFree = figure(Eighth, Eighth)

  val SteadyQuarters =
    MelodyStyle(
      id = "steady-quarters",
      name = "Steady quarters",
      description = "Every note a plain quarter, picked fully at random — the original drill.",
      tier = StyleTier.Beginner,
      figures = listOf(quarter.weighing(1.0)),
      noteWeights = NoteWeights.Uniform,
    )

  val SmoothSteps =
    MelodyStyle(
      id = "smooth-steps",
      name = "Smooth steps",
      description = "Still all quarters, but the notes favor stepwise, singable motion.",
      tier = StyleTier.Beginner,
      figures = listOf(quarter.weighing(1.0)),
      noteWeights = Singable,
    )

  val RoomToBreathe =
    MelodyStyle(
      id = "room-to-breathe",
      name = "Room to breathe",
      description = "Singable quarters with an occasional half note to rest on.",
      tier = StyleTier.Beginner,
      figures = listOf(quarter.weighing(5.0), half.weighing(1.5)),
      noteWeights = Singable,
    )

  val DashOfEighths =
    MelodyStyle(
      id = "dash-of-eighths",
      name = "A dash of eighths",
      description = "Mostly quarters; now and then a stepwise pair of eighths slips through.",
      tier = StyleTier.Beginner,
      figures =
        listOf(
          quarter.weighing(5.0),
          eighthPairUp.weighing(0.8),
          eighthPairDown.weighing(0.8),
          half.weighing(0.8),
        ),
      noteWeights = Singable,
    )

  val WalkingLines =
    MelodyStyle(
      id = "walking-lines",
      name = "Walking lines",
      description = "Quarter-note runs like 1-2-3 and 3-2-1, eighth pairs, and resting halves.",
      tier = StyleTier.Intermediate,
      figures =
        listOf(
          quarter.weighing(4.0),
          figure(Quarter, Quarter, Quarter, contour = listOf(1, 1)).weighing(0.9),
          figure(Quarter, Quarter, Quarter, contour = listOf(-1, -1)).weighing(0.9),
          eighthPairUp.weighing(1.0),
          eighthPairDown.weighing(1.0),
          eighthPairFree.weighing(0.7),
          half.weighing(1.0),
        ),
      noteWeights = Singable,
    )

  val EighthRuns =
    MelodyStyle(
      id = "eighth-runs",
      name = "Eighth runs",
      description = "Flowing four-note eighth runs and turns between calmer quarters.",
      tier = StyleTier.Intermediate,
      figures =
        listOf(
          quarter.weighing(3.0),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(1, 1, 1)).weighing(1.0),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(-1, -1, -1)).weighing(1.0),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(1, -1, -1)).weighing(0.6),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(-1, 1, 1)).weighing(0.6),
          eighthPairUp.weighing(0.6),
          eighthPairDown.weighing(0.6),
          half.weighing(0.7),
        ),
      noteWeights = Singable,
    )

  val DottedDrive =
    MelodyStyle(
      id = "dotted-drive",
      name = "Dotted drive",
      description = "Dotted rhythms and syncopated figures push the line forward.",
      tier = StyleTier.Advanced,
      figures =
        listOf(
          quarter.weighing(2.5),
          figure(DottedQuarter, Eighth).weighing(1.2),
          figure(Eighth, Quarter, Eighth).weighing(0.8),
          eighthPairUp.weighing(0.8),
          eighthPairDown.weighing(0.8),
          eighthPairFree.weighing(0.6),
          half.weighing(0.6),
        ),
      noteWeights = Singable,
    )

  val Quicksilver =
    MelodyStyle(
      id = "quicksilver",
      name = "Quicksilver",
      description = "Sixteenth-note flourishes over dotted and eighth figures — hold on tight.",
      tier = StyleTier.Advanced,
      figures =
        listOf(
          quarter.weighing(2.0),
          figure(Sixteenth, Sixteenth, Sixteenth, Sixteenth, contour = listOf(1, 1, 1))
            .weighing(0.9),
          figure(Sixteenth, Sixteenth, Sixteenth, Sixteenth, contour = listOf(-1, -1, -1))
            .weighing(0.9),
          figure(Sixteenth, Sixteenth, Eighth, contour = listOf(1, 1)).weighing(0.7),
          figure(Sixteenth, Sixteenth, Eighth, contour = listOf(-1, -1)).weighing(0.7),
          figure(DottedQuarter, Eighth).weighing(0.8),
          eighthPairFree.weighing(1.2),
          half.weighing(0.5),
        ),
      noteWeights = Singable,
    )

  // Context-aware styles: these react to the chord the context plays underneath — its tones
  // pull the note picker, and chord-ladder figures arpeggiate it. Without a context (or under a
  // bare drone) they gracefully fall back to their scale-based behavior.

  val ChordTones =
    MelodyStyle(
      id = "chord-tones",
      name = "Chord tones",
      description =
        "Singable quarters drawn to the notes of the chord the context is playing underneath.",
      tier = StyleTier.Beginner,
      figures = listOf(quarter.weighing(5.0), half.weighing(1.0)),
      noteWeights = Singable.copy(chordToneBoost = 4.0),
    )

  val ArpeggioEchoes =
    MelodyStyle(
      id = "arpeggio-echoes",
      name = "Arpeggio echoes",
      description =
        "The melody outlines the context chord: arpeggio runs over its tones, stepwise quarters in between.",
      tier = StyleTier.Intermediate,
      figures =
        listOf(
          quarter.weighing(3.0),
          figure(Quarter, Quarter, Quarter, contour = listOf(1, 1), ladder = FigureLadder.Chord)
            .weighing(1.2),
          figure(Quarter, Quarter, Quarter, contour = listOf(-1, -1), ladder = FigureLadder.Chord)
            .weighing(1.2),
          figure(Eighth, Eighth, contour = listOf(1), ladder = FigureLadder.Chord).weighing(0.8),
          figure(Eighth, Eighth, contour = listOf(-1), ladder = FigureLadder.Chord).weighing(0.8),
          half.weighing(0.8),
        ),
      noteWeights = Singable.copy(chordToneBoost = 3.0),
    )

  val ChordsAndPassingTones =
    MelodyStyle(
      id = "chords-and-passing-tones",
      name = "Chords & passing tones",
      description =
        "Eighth-note lines weaving chord arpeggios and scale steps around the context's changes.",
      tier = StyleTier.Advanced,
      figures =
        listOf(
          quarter.weighing(2.0),
          figure(
            Eighth, Eighth, Eighth, Eighth,
            contour = listOf(1, 1, 1),
            ladder = FigureLadder.Chord,
          ).weighing(1.0),
          figure(
            Eighth, Eighth, Eighth, Eighth,
            contour = listOf(-1, -1, -1),
            ladder = FigureLadder.Chord,
          ).weighing(1.0),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(1, 1, -1)).weighing(0.7),
          figure(Eighth, Eighth, Eighth, Eighth, contour = listOf(-1, -1, 1)).weighing(0.7),
          figure(DottedQuarter, Eighth).weighing(0.6),
          half.weighing(0.6),
        ),
      noteWeights = Singable.copy(chordToneBoost = 2.2),
    )

  /** Matches the behavior levels had before styles existed; also the default for new levels. */
  val Default = SteadyQuarters

  val presets: List<MelodyStyle> =
    listOf(
      SteadyQuarters,
      SmoothSteps,
      RoomToBreathe,
      DashOfEighths,
      ChordTones,
      WalkingLines,
      EighthRuns,
      ArpeggioEchoes,
      DottedDrive,
      Quicksilver,
      ChordsAndPassingTones,
    )
}
