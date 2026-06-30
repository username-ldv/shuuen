package ldv.shuuen.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ldv.shuuen.core.music.MusicLabelDefaults
import ldv.shuuen.core.music.MusicLabelPreset
import ldv.shuuen.core.music.MusicLabelPresets
import ldv.shuuen.core.ui.components.ShuuenUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelEditorSheet(
  editor: LabelEditor,
  labels: List<String>,
  savedCustomLabels: List<String>,
  onLabelChange: (index: Int, value: String) -> Unit,
  onLabelsChange: (List<String>) -> Unit,
  onSaveCustom: (List<String>) -> Unit,
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
    dragHandle = { LabelEditorDragHandle() },
  ) {
    LabelEditorContent(
      editor = editor,
      labels = labels,
      savedCustomLabels = savedCustomLabels,
      onLabelChange = onLabelChange,
      onLabelsChange = onLabelsChange,
      onSaveCustom = onSaveCustom,
      onDismiss = onDismiss,
    )
  }
}

@Composable
private fun LabelEditorContent(
  editor: LabelEditor,
  labels: List<String>,
  savedCustomLabels: List<String>,
  onLabelChange: (index: Int, value: String) -> Unit,
  onLabelsChange: (List<String>) -> Unit,
  onSaveCustom: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  val defaults =
    when (editor) {
      LabelEditor.Notes -> MusicLabelDefaults.NoteNames
      LabelEditor.Degrees -> MusicLabelDefaults.DegreeNames
    }
  val presets =
    when (editor) {
      LabelEditor.Notes -> MusicLabelPresets.NotePresets
      LabelEditor.Degrees -> MusicLabelPresets.DegreePresets
    }
  var values by remember(editor) { mutableStateOf(editableMusicLabels(labels, defaults)) }

  fun loadPreset(presetLabels: List<String>) {
    val next = editableMusicLabels(presetLabels, defaults)
    values = next
    onLabelsChange(next)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()
      .padding(horizontal = 20.dp)
      .padding(bottom = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        Icons.Rounded.TextFields,
        contentDescription = null,
        tint = ShuuenUi.Text,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = if (editor == LabelEditor.Notes) "NOTE NAMES" else "DEGREE NAMES",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold,
          letterSpacing = ShuuenUi.titlesSpacing,
        ),
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      SaveCustomButton(onClick = { onSaveCustom(values) })
      Icon(
        Icons.Rounded.Close,
        contentDescription = "Close",
        tint = ShuuenUi.Muted,
        modifier = Modifier
          .size(28.dp)
          .clip(ShuuenUi.PillShape)
          .clickable(onClick = onDismiss)
          .padding(4.dp),
      )
    }

    PresetStrip(
      presets = presets,
      values = values,
      savedCustomLabels = savedCustomLabels,
      defaults = defaults,
      onLoad = ::loadPreset,
    )

    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      when (editor) {
        LabelEditor.Notes -> {
          labelSection("NATURAL", 0, values, onLabelChange = { index, value ->
            values = values.withValue(index, value)
            onLabelChange(index, value)
          })
          labelSection("SHARP", 7, values, onLabelChange = { index, value ->
            values = values.withValue(index, value)
            onLabelChange(index, value)
          })
          labelSection("FLAT", 14, values, onLabelChange = { index, value ->
            values = values.withValue(index, value)
            onLabelChange(index, value)
          })
        }

        LabelEditor.Degrees -> {
          items(values.indices.toList(), key = { it }) { index ->
            LabelEditorRow(
              reference = MusicLabelDefaults.DegreeNames[index],
              value = values[index],
              placeholder = MusicLabelDefaults.DegreeNames[index],
              onValueChange = { value ->
                values = values.withValue(index, value)
                onLabelChange(index, value)
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PresetStrip(
  presets: List<MusicLabelPreset>,
  values: List<String>,
  savedCustomLabels: List<String>,
  defaults: List<String>,
  onLoad: (List<String>) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "PRESETS",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(presets, key = { it.name }) { preset ->
        val presetLabels = editableMusicLabels(preset.labels, defaults)
        PresetChip(
          label = preset.name,
          selected = values == presetLabels,
          onClick = { onLoad(preset.labels) },
        )
      }
      if (savedCustomLabels.isNotEmpty()) {
        item(key = "saved-custom") {
          val presetLabels = editableMusicLabels(savedCustomLabels, defaults)
          PresetChip(
            label = "Saved custom",
            selected = values == presetLabels,
            onClick = { onLoad(savedCustomLabels) },
          )
        }
      }
    }
  }
}

@Composable
private fun PresetChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .clip(ShuuenUi.PillShape)
      .background(if (selected) ShuuenUi.Inverse else Color.White.copy(alpha = 0.05f))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      color = if (selected) ShuuenUi.OnInverse else ShuuenUi.Muted,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
    )
  }
}

@Composable
private fun SaveCustomButton(onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(ShuuenUi.PillShape)
      .background(Color.White.copy(alpha = 0.06f))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      Icons.Rounded.Save,
      contentDescription = null,
      tint = ShuuenUi.Text,
      modifier = Modifier.size(16.dp),
    )
    Text(
      text = "Save",
      color = ShuuenUi.Muted,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
    )
  }
}

private fun LazyListScope.labelSection(
  title: String,
  startIndex: Int,
  values: List<String>,
  onLabelChange: (index: Int, value: String) -> Unit,
) {
  item(key = title) {
    Text(
      text = title,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
      modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
  }
  items((startIndex until startIndex + 7).toList(), key = { it }) { index ->
    LabelEditorRow(
      reference = MusicLabelDefaults.NoteNames[index],
      value = values[index],
      placeholder = MusicLabelDefaults.NoteNames[index],
      onValueChange = { onLabelChange(index, it) },
    )
  }
}

@Composable
private fun LabelEditorRow(
  reference: String,
  value: String,
  placeholder: String,
  onValueChange: (String) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShuuenUi.ControlShape)
      .background(Color.White.copy(alpha = 0.05f))
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = reference,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelLarge.copy(letterSpacing = (-2).sp),
      modifier = Modifier.width(42.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Box(modifier = Modifier.weight(1f)) {
      if (value.isEmpty()) {
        Text(
          text = placeholder,
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.labelLarge.copy(letterSpacing = (-2).sp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = ShuuenUi.Text),
        cursorBrush = SolidColor(ShuuenUi.Text),
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun LabelEditorDragHandle() {
  Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .size(width = 36.dp, height = 4.dp)
        .clip(RoundedCornerShape(50))
        .background(ShuuenUi.HairlineStrong),
    )
  }
}

private fun editableMusicLabels(
  customNames: List<String>,
  defaultNames: List<String>,
): List<String> =
  List(defaultNames.size) { index -> customNames.getOrNull(index) ?: defaultNames[index] }

private fun List<String>.withValue(index: Int, value: String): List<String> =
  mapIndexed { itemIndex, itemValue -> if (itemIndex == index) value else itemValue }
