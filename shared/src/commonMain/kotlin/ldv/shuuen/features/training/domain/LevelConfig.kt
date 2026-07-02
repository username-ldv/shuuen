package ldv.shuuen.features.training.domain

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.PlatformFileSerializer
import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.MelodyStyles

@Serializable
sealed interface LevelConfig {
  @Serializable
  sealed interface Singles {
    val rotateEveryQuestions: Int?

    @Serializable
    data class Absolute(
      val scales: List<ScaleConfig.AbsoluteScaleConfig>, override val rotateEveryQuestions: Int? = null
    ) : Singles

    @Serializable
    data class Relative(
      val scaleConfig: ScaleConfig.RelativeScaleConfig, override val rotateEveryQuestions: Int? = 10
    ) : Singles
  }

  @Serializable
  sealed interface Chords {
    val rotateEveryQuestions: Int?

    @Serializable
    data class Absolute(
      val scales: List<ScaleConfig.AbsoluteScaleConfig>, override val rotateEveryQuestions: Int? = null
    ) : Chords

    @Serializable
    data class Relative(
      val scaleConfig: ScaleConfig.RelativeScaleConfig, override val rotateEveryQuestions: Int? = 10
    ) : Chords
  }

  @Serializable
  sealed interface Melodies {
    /** Randomly generated melodies from a scale; all quiz parameters live here. */
    @Serializable
    data class Random(
      val scaleConfig: ScaleConfig,
      /** null means an unlimited session. Ignored (always unlimited) when [notesPerSequence] is null. */
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
       * Rhythm figures and note-picker weights shaping the generated melodies. The default
       * matches levels saved before styles existed: uniformly random quarter notes.
       */
      val melodyStyle: MelodyStyle = MelodyStyles.Default,
    ) : Melodies

    /**
     * A melody read from a user-picked MIDI file. Only a reference to the file is stored (path on
     * desktop, URI on Android) — the bytes are re-read when the level is played.
     */
    @Serializable
    data class Midi(
      @Serializable(with = PlatformFileSerializer::class) val file: PlatformFile,
      val fileName: String,
      val useOriginalVelocities: Boolean = false,
    ) : Melodies
  }
}


@Serializable
sealed interface ScaleConfig {
  val scaleType: ScaleType

  @Serializable
  sealed interface ScaleItemState {
    @Serializable
    data class ScaleDegreeState(val degree: Degree, val active: Boolean) : ScaleItemState

    @Serializable
    data class ScalePitchState(val pitch: Pitch, val active: Boolean) : ScaleItemState
  }

  @Serializable
  data class AbsoluteScaleConfig(
    val root: Pitch, override val scaleType: ScaleType, val pitchStates: List<ScaleItemState.ScalePitchState>
  ) : ScaleConfig

  @Serializable
  data class RelativeScaleConfig(
    override val scaleType: ScaleType, val degreeStates: List<ScaleItemState.ScaleDegreeState>
  ) : ScaleConfig
}

