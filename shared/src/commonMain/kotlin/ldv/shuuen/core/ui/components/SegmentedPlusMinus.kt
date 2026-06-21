package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SegmentedPlusMinus(
  value: Int?,
  modifier: Modifier = Modifier,
  onChange: (Int?) -> Unit = {},
  delta: Int = 5,
  minimalNumber: Int = 0
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(54.dp).clip(ldv.shuuen.core.ui.components.ShuuenUi.PillShape)
      .background(Color.White.copy(alpha = 0.05f)).then(modifier),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SegmentedPart("—", onClick = {
      onChange(value?.let {
        val v = it - delta
        if (v < minimalNumber) minimalNumber else v
      })
    })
    VerticalDivider(color = ldv.shuuen.core.ui.components.ShuuenUi.Hairline)
    Box(
      modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center
    ) {
      BasicTextField(
        value = value?.toString() ?: "",
        onValueChange = { v ->
          val newValue = v.toUIntOrNull() ?: return@BasicTextField onChange(null)
          onChange(newValue.toInt())
        },
        textStyle = MaterialTheme.typography.headlineMedium.copy(
          color = LocalContentColor.current, textAlign = TextAlign.Center
        ),
        singleLine = true,
        cursorBrush = SolidColor(LocalContentColor.current),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    }
    VerticalDivider(color = ldv.shuuen.core.ui.components.ShuuenUi.Hairline)
    SegmentedPart("+", onClick = {
      onChange(value?.let { it + delta })
    })
  }
}

@Composable
private fun RowScope.SegmentedPart(text: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier.weight(0.45f).fillMaxHeight().clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
      style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
    )
  }
}

@Preview
@Composable
fun SegmentedPlusMinusPreview() {
  ldv.shuuen.core.ui.theme.ShuuenTheme {
    ldv.shuuen.core.ui.components.SegmentedPlusMinus(15)
  }
}
