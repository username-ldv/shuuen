package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var tonic: Pitch? by rememberSaveable { mutableStateOf(null) }
    var mode by rememberSaveable { mutableStateOf(ScaleType.Major) }
    LaunchedEffect(tonic, mode) {
      val newScaleConfig: ScaleConfig = tonic?.let { t ->
        val pitchStates = Scale.fromScaleType(t, mode, listOf(0)).asPitchStates()
        ScaleConfig.AbsoluteScaleConfig(t, mode, pitchStates)
      } ?: run {
        val degreeStates1 = Scale.fromScaleType(Pitch.C, mode, listOf(0)).asConfigDegreeStates()
        ScaleConfig.RelativeScaleConfig(scaleType = mode, degreeStates = degreeStates1)
      }
      onScaleChosen(newScaleConfig)
    }
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
          onItemSelected = { tonic = Pitch.fromName(it) },
          modifier = Modifier.weight(0.75f)
        )
        TextDropdownMenu(
          items = ScaleType.entries.map { it.toString() },
          selectedItem = mode.toString(),
          onItemSelected = {
            mode = ScaleType.fromName(it) ?: error("invalid scale")
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
            val config = ScaleConfig.RelativeScaleConfig(mode, degreeStates)
            onScaleChosen(config)
          })
        }

        is ScaleConfig.AbsoluteScaleConfig -> {
          BoxedItemRow(items = scaleConfig.pitchStates.toBoxedItems(), onClick = { pitch ->
            val pitchStates =
              scaleConfig.pitchStates.updateBy(condition = { it.pitch == pitch }) { previous ->
                ScaleConfig.ScaleItemState.ScalePitchState(pitch, !previous.active)
              }
            val config = ScaleConfig.AbsoluteScaleConfig(scaleConfig.root, mode, pitchStates)
            onScaleChosen(config)
          })
        }
      }
    }
  }
}
