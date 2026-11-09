package ldv.shuuen.features.training.level

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.PlatformFileSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.generator.ChordStyle
import ldv.shuuen.core.music.generator.ChordStyles
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.MelodyStyles

/**
 * Stored in Room as JSON (see the level type converters). The subclasses keep the serial names they
 * had in their original package, `features.training.domain`: those names are in every saved level.
 * Never change a serial name here without a database migration that rewrites the stored JSON.
 */
@Serializable
sealed interface LevelConfig {
  @Serializable
  sealed interface Singles {
    val rotateEveryQuestions: Int?

    /**
     * Each quiz note plays randomly out of tune by up to ± this many cents; 0 is off. The default
     * matches levels saved before the setting existed: perfectly tuned notes.
     */
    val tuneInconsistencyCents: Int

    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Singles.Absolute")
    data class Absolute(
      val scales: List<ScaleConfig.AbsoluteScaleConfig>,
      override val rotateEveryQuestions: Int? = null,
      override val tuneInconsistencyCents: Int = 0,
    ) : Singles

    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Singles.Relative")
    data class Relative(
      val scaleConfig: ScaleConfig.RelativeScaleConfig,
      override val rotateEveryQuestions: Int? = 10,
      override val tuneInconsistencyCents: Int = 0,
    ) : Singles
  }

  @Serializable
  sealed interface Chords {
    val rotateEveryQuestions: Int?

    /**
     * Weighted chord figures shaping the generated chords. Lives inside the config (stored as one
     * JSON column by the Room converter) so it survives the entity round-trip without a schema
     * change. The default matches levels saved before styles existed: fully random note stacks.
     */
    val chordStyle: ChordStyle

    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Chords.Absolute")
    data class Absolute(
      val scales: List<ScaleConfig.AbsoluteScaleConfig>,
      override val rotateEveryQuestions: Int? = null,
      override val chordStyle: ChordStyle = ChordStyles.Default,
    ) : Chords

    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Chords.Relative")
    data class Relative(
      val scaleConfig: ScaleConfig.RelativeScaleConfig,
      override val rotateEveryQuestions: Int? = 10,
      override val chordStyle: ChordStyle = ChordStyles.Default,
    ) : Chords
  }

  @Serializable
  sealed interface Melodies {
    /** Randomly generated melodies from a scale; all quiz parameters live here. */
    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Melodies.Random")
    data class Random(
      val scaleConfig: ScaleConfig,
      /**
       * null means an unlimited session. Ignored (always unlimited) when [notesPerSequence] is
       * null.
       */
      val questionsNumber: Int?,
      /** Notes per question sequence; null means one endless, continuously playing stream. */
      val notesPerSequence: Int?,
      val tempo: Int,
      val range: NoteRange,
      /**
       * Move to a new random tonic every this many questions; null is off. Only applies to a
       * relative (random-tonic) scale with finite sequences.
       */
      val rotateEveryQuestions: Int? = null,
      /**
       * Rhythm figures and note-picker weights shaping the generated melodies. The default matches
       * levels saved before styles existed: uniformly random quarter notes.
       */
      val melodyStyle: MelodyStyle = MelodyStyles.Default,
      /** Each note plays randomly out of tune by up to ± this many cents; 0 is off. */
      val tuneInconsistencyCents: Int = 0,
    ) : Melodies

    /**
     * A melody read from a user-picked MIDI file. Only a reference to the file is stored (path on
     * desktop, URI on Android) — the bytes are re-read when the level is played.
     *
     * [backingFile] is an optional audio file (e.g. the real song's MP3) played in sync with the
     * melody; [backingOffsetMs] is the position in that audio matching the MIDI's time zero
     * (positive when the audio has an intro before the MIDI's first tick).
     */
    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.LevelConfig.Melodies.Midi")
    data class Midi(
      @SerialName("file")
      @Serializable(with = MidiFileSourceSerializer::class)
      val midiSource: MidiFileSource,
      val fileName: String,
      val useOriginalVelocities: Boolean = false,
      @Serializable(with = PlatformFileSerializer::class) val backingFile: PlatformFile? = null,
      val backingFileName: String? = null,
      val backingOffsetMs: Long = 0,
      /** Labelled key of a catalog melody; null for unlabelled or locally imported files. */
      val key: MidiKey? = null,
    ) : Melodies {
      /** Source-compatible constructor for locally created levels. */
      constructor(
        file: PlatformFile,
        fileName: String,
        useOriginalVelocities: Boolean = false,
        backingFile: PlatformFile? = null,
        backingFileName: String? = null,
        backingOffsetMs: Long = 0,
      ) : this(
        midiSource = MidiFileSource.Local(file),
        fileName = fileName,
        useOriginalVelocities = useOriginalVelocities,
        backingFile = backingFile,
        backingFileName = backingFileName,
        backingOffsetMs = backingOffsetMs,
      )

      /** Existing local setup/edit code intentionally remains local-file-only. */
      val file: PlatformFile
        get() =
          (midiSource as? MidiFileSource.Local)?.platformFile
            ?: error("A backend MIDI level has no local PlatformFile.")
    }
  }
}

/** Stored inside [LevelConfig]; the same rule about serial names applies. */
@Serializable
sealed interface ScaleConfig {
  val scaleType: ScaleType

  @Serializable
  sealed interface ScaleItemState {
    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.ScaleConfig.ScaleItemState.ScaleDegreeState")
    data class ScaleDegreeState(val degree: Degree, val active: Boolean) : ScaleItemState

    @Serializable
    @SerialName("ldv.shuuen.features.training.domain.ScaleConfig.ScaleItemState.ScalePitchState")
    data class ScalePitchState(val pitch: Pitch, val active: Boolean) : ScaleItemState
  }

  @Serializable
  @SerialName("ldv.shuuen.features.training.domain.ScaleConfig.AbsoluteScaleConfig")
  data class AbsoluteScaleConfig(
    val root: Pitch,
    override val scaleType: ScaleType,
    val pitchStates: List<ScaleItemState.ScalePitchState>,
  ) : ScaleConfig

  @Serializable
  @SerialName("ldv.shuuen.features.training.domain.ScaleConfig.RelativeScaleConfig")
  data class RelativeScaleConfig(
    override val scaleType: ScaleType,
    val degreeStates: List<ScaleItemState.ScaleDegreeState>,
  ) : ScaleConfig
}
