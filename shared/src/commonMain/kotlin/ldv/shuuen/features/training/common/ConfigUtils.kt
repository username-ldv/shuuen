package ldv.shuuen.features.training.common

import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.core.ui.components.BoxedListItemState

@JvmName("pitchStatesToBoxedItems")
fun Iterable<ScaleConfig.ScaleItemState.ScalePitchState>.toBoxedItems(names: Map<Pitch, String>? = null): Map<Pitch, BoxedListItemState> {
  return this.associate {
    it.pitch to BoxedListItemState(
      active = it.active, label = names?.get(it.pitch) ?: it.pitch.toString()
    )
  }
}

@JvmName("degreesToBoxedItems")
fun Iterable<ScaleConfig.ScaleItemState.ScaleDegreeState>.toBoxedItems(names: Map<Degree, String>? = null): Map<Degree, BoxedListItemState> {
  return this.associate {
    it.degree to BoxedListItemState(
      active = it.active, label = names?.get(it.degree) ?: it.degree.toString()
    )
  }
}

fun Scale.asConfigDegreeStates(active: Boolean = true): List<ScaleConfig.ScaleItemState.ScaleDegreeState> {
  return this.degrees.map {
    ScaleConfig.ScaleItemState.ScaleDegreeState(
      active = active, degree = it
    )
  }
}

fun Scale.asPitchStates(active: Boolean = true): List<ScaleConfig.ScaleItemState.ScalePitchState> {
  return this.pitches.map {
    ScaleConfig.ScaleItemState.ScalePitchState(
      active = active, pitch = it
    )
  }
}