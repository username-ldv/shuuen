package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.ui.theme.ShuuenTheme


@Composable
fun <T> BoxedItemRow(
  items: Map<T, BoxedListItemState>,
  modifier: Modifier = Modifier,
  itemSize: Dp = 50.dp,
  onClick: (T) -> Unit = {}
) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
    items.forEach { pitch ->
      item {
        BoxedListItem(
          label = pitch.value.label,
          active = pitch.value.active,
          onClicked = { onClick(pitch.key) },
          itemSize = itemSize
        )
      }
    }
  }
}

@Preview
@Composable
fun BoxedItemRowPreview() {
  ShuuenTheme {
    BoxedItemRow(items = Scale.major(Pitch.C).pitches.map {
      it to BoxedListItemState(
        true, it.toString()
      )
    }.toMap())
  }
}