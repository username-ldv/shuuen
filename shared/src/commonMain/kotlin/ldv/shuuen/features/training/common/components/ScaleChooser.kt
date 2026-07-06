package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.util.updateBy
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.TextDropdownMenu
import ldv.shuuen.core.ui.components.BoxedItemRow
import ldv.shuuen.features.training.common.asConfigDegreeStates
import ldv.shuuen.features.training.common.asPitchStates
import ldv.shuuen.features.training.common.toBoxedItems

@Composable
fun ScaleChooser(scaleConfig: ScaleConfig, onScaleChosen: (ScaleConfig) -> Unit = {}) {
  FlatSection(
    label = "1 · SCALE",
    supporting = "Choose the scale you want to train.",
  ) {
    val tonic = (scaleConfig as? ScaleConfig.AbsoluteScaleConfig)?.root
    val mode = scaleConfig.scaleType
    Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()
      ) {
        TextDropdownMenu(
          items = listOf("Random") + Pitch.entries.map {
            Scale.appropriatePitchName(
              it, it, mode
            )
          },
          selectedItem = tonic?.let { Scale.appropriatePitchName(it, it, mode) } ?: "Random",
          onItemSelected = { name ->
            onScaleChosen(defaultScaleConfig(Pitch.fromName(name), mode))
          },
          modifier = Modifier.weight(0.75f)
        )
        TextDropdownMenu(
          items = ScaleType.entries.map { it.toString() },
          selectedItem = mode.toString(),
          onItemSelected = {
            val selectedMode = ScaleType.fromName(it) ?: error("invalid scale")
            onScaleChosen(defaultScaleConfig(tonic, selectedMode))
          },
          modifier = Modifier.weight(1f)
        )
      }
      when (scaleConfig) {
        is ScaleConfig.RelativeScaleConfig -> {
          BoxedItemRow(items = scaleConfig.degreeStates.toBoxedItems(), onClick = { degree ->
            val degreeStates =
              scaleConfig.degreeStates.updateBy(condition = { it.degree == degree }) { previous ->
                ScaleConfig.ScaleItemState.ScaleDegreeState(degree, !previous.active)
              }
            val config = ScaleConfig.RelativeScaleConfig(scaleConfig.scaleType, degreeStates)
            onScaleChosen(config)
          })
        }

        is ScaleConfig.AbsoluteScaleConfig -> {
          BoxedItemRow(items = scaleConfig.pitchStates.toBoxedItems(), onClick = { pitch ->
            val pitchStates =
              scaleConfig.pitchStates.updateBy(condition = { it.pitch == pitch }) { previous ->
                ScaleConfig.ScaleItemState.ScalePitchState(pitch, !previous.active)
              }
            val config = ScaleConfig.AbsoluteScaleConfig(
              scaleConfig.root,
              scaleConfig.scaleType,
              pitchStates,
            )
            onScaleChosen(config)
          })
        }
      }
    }
  }
}

private fun defaultScaleConfig(tonic: Pitch?, mode: ScaleType): ScaleConfig =
  tonic?.let { root ->
    ScaleConfig.AbsoluteScaleConfig(
      root = root,
      scaleType = mode,
      pitchStates = Scale.fromScaleType(root, mode, listOf(0)).asPitchStates(),
    )
  } ?: ScaleConfig.RelativeScaleConfig(
    scaleType = mode,
    degreeStates = Scale.fromScaleType(Pitch.C, mode, listOf(0)).asConfigDegreeStates(),
  )
