package ldv.shuuen.features.training.melodies.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.core.music.generator.StyleTier
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Rhythm style picker: the predefined [MelodyStyles.presets] grouped by tier. Tapping a style
 * selects it; a custom style editor will join these presets later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmStyleSheet(
  selected: MelodyStyle,
  onSelect: (MelodyStyle) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ShuuenUi.Surface,
    contentColor = ShuuenUi.Text,
    scrimColor = Color.Black.copy(alpha = 0.6f),
    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    dragHandle = { SheetDragHandle() },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          Icons.Rounded.MusicNote,
          contentDescription = null,
          tint = ShuuenUi.Text,
          modifier = Modifier.size(22.dp),
        )
        Text(
          text = "RHYTHM",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = ShuuenUi.titlesSpacing,
          ),
        )
      }
      Text(
        text = "How the random notes flow: each style mixes rhythm figures with a weighted note picker, from plain quarters to livelier, more melodic lines.",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 4.dp),
      )

      StyleTier.entries.forEach { tier ->
        val styles = MelodyStyles.presets.filter { it.tier == tier }
        if (styles.isEmpty()) return@forEach
        Text(
          text = tier.label.uppercase(),
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = ShuuenUi.labelSpacing,
            fontWeight = FontWeight.SemiBold,
          ),
          modifier = Modifier.padding(top = 8.dp),
        )
        styles.forEach { style ->
          StyleRow(
            style = style,
            selected = style.id == selected.id,
            onClick = { onSelect(style) },
          )
        }
      }
    }
  }
}

@Composable
private fun StyleRow(
  style: MelodyStyle,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = style.name,
        color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall.copy(
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
      )
      Text(
        text = style.description,
        color = if (selected) ShuuenUi.OnInverse.copy(alpha = 0.65f) else ShuuenUi.Muted,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (selected) {
      Icon(
        Icons.Rounded.Check,
        contentDescription = null,
        tint = ShuuenUi.OnInverse,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun SheetDragHandle() {
  Box(
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(width = 36.dp, height = 4.dp)
        .clip(RoundedCornerShape(50))
        .background(ShuuenUi.HairlineStrong),
    )
  }
}
