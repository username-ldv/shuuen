package ldv.shuuen.features.training.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import ldv.shuuen.core.ui.components.CircleIconButton

enum class LevelSortOrder {
  Ascending,
  Descending;

  fun toggled(): LevelSortOrder =
    when (this) {
      Ascending -> Descending
      Descending -> Ascending
    }
}

/** Level IDs are UUIDv7 values, so their canonical text order is also their creation order. */
fun <T> List<T>.sortedByLevelCreation(
  order: LevelSortOrder,
  id: (T) -> String,
): List<T> =
  sortedWith(
    Comparator { left, right ->
      when (order) {
        LevelSortOrder.Ascending -> id(left).compareTo(id(right))
        LevelSortOrder.Descending -> id(right).compareTo(id(left))
      }
    }
  )

@Composable
fun LevelSortAction(
  order: LevelSortOrder,
  onOrderChange: (LevelSortOrder) -> Unit,
) {
  val ascending = order == LevelSortOrder.Ascending
  CircleIconButton(
    icon = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
    contentDescription =
      if (ascending) "Oldest levels first. Sort newest first"
      else "Newest levels first. Sort oldest first",
    onClick = { onOrderChange(order.toggled()) },
  )
}
