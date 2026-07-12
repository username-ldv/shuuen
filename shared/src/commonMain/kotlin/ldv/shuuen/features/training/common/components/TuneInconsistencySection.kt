package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl

/** How far a level's tune inconsistency can go, in ± cents. */
val TuneInconsistencyRange = 0..70

/**
 * Level-creator section for the tune inconsistency setting: a slider over
 * [TuneInconsistencyRange] with an exact-value box. [label] carries the screen's own section
 * numbering.
 */
@Composable
fun TuneInconsistencySection(
  label: String,
  cents: Int,
  onChange: (Int) -> Unit,
) {
  FlatSection(
    label = label,
    supporting = "Each note plays randomly out of tune by up to ± this many cents.",
    trailing = {
      NumberInputBox(
        value = cents,
        range = TuneInconsistencyRange,
        suffix = "¢",
        onChange = onChange,
      )
    },
  ) {
    Slider(
      value = cents.toFloat(),
      onValueChange = { onChange(it.roundToInt()) },
      valueRange =
        TuneInconsistencyRange.first.toFloat()..TuneInconsistencyRange.last.toFloat(),
      colors =
        SliderDefaults.colors(
          thumbColor = ShuuenUi.Text,
          activeTrackColor = ShuuenUi.Text,
          inactiveTrackColor = ShuuenUi.Hairline,
        ),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        "Off",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        "±${TuneInconsistencyRange.last}¢",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

/** A small numeric field with a unit [suffix]; values outside [range] clamp high, wait low. */
@Composable
fun NumberInputBox(
  value: Int,
  range: IntRange,
  suffix: String,
  onChange: (Int) -> Unit,
) {
  // Recreated whenever the VM value changes (e.g. slider drags); incomplete input like "3" while
  // typing "360" stays local until it becomes a valid value.
  var text by remember(value) { mutableStateOf(value.toString()) }
  SoftControl(modifier = Modifier.width(110.dp)) {
    BasicTextField(
      value = text,
      onValueChange = { newText ->
        text = newText
        val typed = newText.toIntOrNull() ?: return@BasicTextField
        when {
          typed in range -> onChange(typed)
          typed > range.last -> onChange(range.last)
        }
      },
      textStyle =
        MaterialTheme.typography.titleSmall.copy(
          color = ShuuenUi.Text,
          textAlign = TextAlign.End,
        ),
      singleLine = true,
      cursorBrush = SolidColor(ShuuenUi.Text),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.weight(1f),
    )
    Text(
      suffix,
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
    )
  }
}
