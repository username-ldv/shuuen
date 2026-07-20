package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.ui.theme.ShuuenTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextDropdownMenu(
  items: List<String>,
  selectedItem: String,
  onItemSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = selectedItem,
      onValueChange = {},
      enabled = false,
      shape = ldv.shuuen.core.ui.components.ShuuenUi.ControlShape,
      colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
        disabledTextColor = ldv.shuuen.core.ui.components.ShuuenUi.Text,
        disabledBorderColor = ldv.shuuen.core.ui.components.ShuuenUi.HairlineStrong,
        disabledTrailingIconColor = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
        disabledLabelColor = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
        disabledPlaceholderColor = ldv.shuuen.core.ui.components.ShuuenUi.Muted,
      ),
      readOnly = true,
      singleLine = true,
      trailingIcon = {
        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
      },
      modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        .fillMaxWidth(),
    )

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = {
        expanded = false
        focusManager.clearFocus()
      },
      modifier = Modifier.exposedDropdownSize(matchAnchorWidth = true),
    ) {
      items.forEach { item ->
        DropdownMenuItem(
          text = { Text(item) },
          onClick = {
            onItemSelected(item)
            expanded = false
            focusManager.clearFocus()
          },
          trailingIcon = {
            if (item == selectedItem) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
              )
            }
          },
        )
      }
    }
  }
}

/**
 * Same tap-to-open menu as [TextDropdownMenu], but anchored on a compact 34dp pill
 * instead of an [OutlinedTextField] — visually matching the inline pill controls
 * (e.g. [ldv.shuuen.core.ui.components.music.OctaveStepper]) so it sits flush beside them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDropdownMenu(
  items: List<String>,
  selectedItem: String,
  onItemSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        .height(34.dp)
        .clip(ShuuenUi.PillShape)
        .background(ShuuenUi.Ink.copy(alpha = 0.05f))
        .padding(start = 16.dp, end = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = selectedItem,
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
        contentDescription = null,
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(20.dp),
      )
    }

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = {
        expanded = false
        focusManager.clearFocus()
      },
      shape = ShuuenUi.ControlShape,
      modifier = Modifier.exposedDropdownSize(matchAnchorWidth = true),
    ) {
      items.forEach { item ->
        DropdownMenuItem(
          text = { Text(item) },
          onClick = {
            onItemSelected(item)
            expanded = false
            focusManager.clearFocus()
          },
          trailingIcon = {
            if (item == selectedItem) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
              )
            }
          },
        )
      }
    }
  }
}

@Preview
@Composable
private fun TextDropdownMenuPreview() {
  ShuuenTheme {
    Column {
      TextDropdownMenu(
        items = Pitch.entries.map { it.toString() },
        selectedItem = Pitch.C.toString(),
        onItemSelected = {},
      )
      CompactDropdownMenu(
        items = Pitch.entries.map { it.toString() },
        selectedItem = Pitch.C.toString(),
        onItemSelected = {})
    }
  }
}
