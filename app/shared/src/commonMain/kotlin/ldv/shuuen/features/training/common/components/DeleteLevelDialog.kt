package ldv.shuuen.features.training.common.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DeleteLevelDialog(
  levelName: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Remove level?") },
    text = {
      Text("\"$levelName\" will be removed from this level select. Past results stay saved.")
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("REMOVE")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("CANCEL")
      }
    },
  )
}
