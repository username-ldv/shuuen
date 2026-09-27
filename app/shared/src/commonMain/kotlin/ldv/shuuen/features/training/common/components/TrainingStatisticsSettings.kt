package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.CircleIconButton
import ldv.shuuen.core.ui.components.ShuuenSwitch
import ldv.shuuen.core.ui.components.ShuuenUi

private enum class StatisticsDeletion {
  LastPlay,
  All,
}

@Composable
fun CourseSettingsAction(onClick: () -> Unit) {
  CircleIconButton(
    icon = Icons.Rounded.Settings,
    contentDescription = "Course settings",
    onClick = onClick,
  )
}

/** Shared settings sheet for every training level, with room for mode-specific options. */
@Composable
fun LevelSettingsSheet(
  levelName: String,
  hasStatistics: Boolean,
  onDeleteLastPlayStatistics: () -> Unit,
  onDeleteAllStatistics: () -> Unit,
  onDismiss: () -> Unit,
  additionalContent: @Composable ColumnScope.() -> Unit = {},
) {
  var pendingDeletion by remember(levelName) { mutableStateOf<StatisticsDeletion?>(null) }

  SettingsSheetScaffold(
    title = "LEVEL SETTINGS",
    subjectName = levelName,
    onDismiss = onDismiss,
  ) {
    additionalContent()
    StatisticsSettingsSection(
      hasStatistics = hasStatistics,
      onDeleteLast = { pendingDeletion = StatisticsDeletion.LastPlay },
      onDeleteAll = { pendingDeletion = StatisticsDeletion.All },
    )
  }

  pendingDeletion?.let { deletion ->
    DeleteStatisticsDialog(
      title =
        when (deletion) {
          StatisticsDeletion.LastPlay -> "Delete last play statistics?"
          StatisticsDeletion.All -> "Delete all level statistics?"
        },
      message =
        when (deletion) {
          StatisticsDeletion.LastPlay ->
            "The most recent saved play statistics for \"$levelName\" will be permanently deleted."
          StatisticsDeletion.All ->
            "All saved play statistics for \"$levelName\" will be permanently deleted."
        },
      onConfirm = {
        when (deletion) {
          StatisticsDeletion.LastPlay -> onDeleteLastPlayStatistics()
          StatisticsDeletion.All -> onDeleteAllStatistics()
        }
        pendingDeletion = null
      },
      onDismiss = { pendingDeletion = null },
    )
  }
}

/**
 * Settings for a whole course. The tune inconsistency section shows only when
 * [onTuneInconsistencyChange] is given; [tuneInconsistencyCents] is the course's local override,
 * null while every level keeps its own value.
 */
@Composable
fun CourseSettingsSheet(
  courseName: String,
  onDeleteAllStatistics: () -> Unit,
  onDismiss: () -> Unit,
  tuneInconsistencyCents: Int? = null,
  onTuneInconsistencyChange: ((Int?) -> Unit)? = null,
) {
  var confirmDeletion by remember(courseName) { mutableStateOf(false) }

  SettingsSheetScaffold(
    title = "COURSE SETTINGS",
    subjectName = courseName,
    onDismiss = onDismiss,
  ) {
    if (onTuneInconsistencyChange != null) {
      CourseTuneInconsistencySection(
        cents = tuneInconsistencyCents,
        onChange = onTuneInconsistencyChange,
      )
    }
    SettingsSectionLabel("STATISTICS")
    DestructiveSettingsAction(
      label = "Delete all statistics",
      supportingText = "Deletes saved plays for every level and mode in this course.",
      icon = Icons.Rounded.DeleteSweep,
      onClick = { confirmDeletion = true },
    )
  }

  if (confirmDeletion) {
    DeleteStatisticsDialog(
      title = "Delete all course statistics?",
      message =
        "All saved play statistics for \"$courseName\", across every mode, will be permanently deleted.",
      onConfirm = {
        onDeleteAllStatistics()
        confirmDeletion = false
        onDismiss()
      },
      onDismiss = { confirmDeletion = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheetScaffold(
  title: String,
  subjectName: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ShuuenUi.Surface,
    contentColor = ShuuenUi.Text,
    scrimColor = Color.Black.copy(alpha = 0.6f),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          color = ShuuenUi.Text,
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.SemiBold,
              letterSpacing = ShuuenUi.titlesSpacing,
            ),
        )
        Text(
          text = subjectName,
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      content()
    }
  }
}

/**
 * Switch plus slider for a course-wide tune inconsistency. Slider drags stay local and are saved on
 * release, so a drag doesn't rewrite the settings file on every frame.
 */
@Composable
private fun CourseTuneInconsistencySection(
  cents: Int?,
  onChange: (Int?) -> Unit,
) {
  var draft by remember(cents) { mutableIntStateOf(cents ?: 0) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SettingsSectionLabel("TUNE INCONSISTENCY")
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Replace level settings", color = ShuuenUi.Text, style = MaterialTheme.typography.titleSmall)
        Text(
          if (cents == null) "Each level plays with its own tune inconsistency."
          else "Every level in this course uses this value instead of its own, on this device only.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      ShuuenSwitch(
        checked = cents != null,
        onCheckedChange = { enabled -> onChange(if (enabled) draft else null) },
      )
    }
    if (cents != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          "Each note plays randomly out of tune by up to ± this many cents.",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        NumberInputBox(
          value = draft,
          range = TuneInconsistencyRange,
          suffix = "¢",
          onChange = {
            draft = it
            onChange(it)
          },
        )
      }
      TuneInconsistencySlider(
        cents = draft,
        onChange = { draft = it },
        onChangeFinished = { onChange(draft) },
      )
    }
  }
}

@Composable
private fun StatisticsSettingsSection(
  hasStatistics: Boolean,
  onDeleteLast: () -> Unit,
  onDeleteAll: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SettingsSectionLabel("STATISTICS")
    DestructiveSettingsAction(
      label = "Delete last play statistics",
      supportingText = "Removes only the most recent saved play.",
      icon = Icons.Rounded.History,
      enabled = hasStatistics,
      onClick = onDeleteLast,
    )
    DestructiveSettingsAction(
      label = "Delete all statistics",
      supportingText = "Removes every saved play for this level.",
      icon = Icons.Rounded.DeleteSweep,
      enabled = hasStatistics,
      onClick = onDeleteAll,
    )
  }
}

@Composable
private fun SettingsSectionLabel(text: String) {
  Text(
    text = text,
    color = ShuuenUi.Muted,
    style =
      MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = ShuuenUi.titlesSpacing,
      ),
  )
}

@Composable
private fun DestructiveSettingsAction(
  label: String,
  supportingText: String,
  icon: ImageVector,
  onClick: () -> Unit,
  enabled: Boolean = true,
) {
  val actionColor = if (enabled) ShuuenUi.Incorrect else ShuuenUi.Muted
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent,
    contentColor = actionColor,
    shape = ShuuenUi.ControlShape,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
          supportingText,
          color = if (enabled) ShuuenUi.Dim else ShuuenUi.Muted,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun DeleteStatisticsDialog(
  title: String,
  message: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text("DELETE") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("CANCEL") }
    },
  )
}
