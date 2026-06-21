package ldv.shuuen.features.training.domain

import kotlinx.serialization.Serializable
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType

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
      val scaleConfig: ScaleConfig.RelativeScaleConfig, override val rotateEveryQuestions: Int? = 5
    ) : Singles
  }
}


@Serializable
sealed interface ScaleConfig {

  @Serializable
  sealed interface ScaleItemState {
    @Serializable
    data class ScaleDegreeState(val degree: Degree, val active: Boolean) : ScaleItemState

    @Serializable
    data class ScalePitchState(val pitch: Pitch, val active: Boolean) : ScaleItemState
  }

  @Serializable
  data class AbsoluteScaleConfig(
    val root: Pitch, val scaleType: ScaleType, val pitchStates: List<ScaleItemState.ScalePitchState>
  ) : ScaleConfig

  @Serializable
  data class RelativeScaleConfig(
    val scaleType: ScaleType, val degreeStates: List<ScaleItemState.ScaleDegreeState>
  ) : ScaleConfig
}

