package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor
import org.koin.compose.koinInject

/** App-bar indicator for the optional backend. Unlike the MIDI badge, it remains visible offline. */
@Composable
fun BackendStatusBadge(modifier: Modifier = Modifier) {
  val monitor = koinInject<BackendStatusMonitor>()
  val status by monitor.status.collectAsStateWithLifecycle()
  BackendStatusIcon(status, modifier.padding(end = 10.dp))
}

@Composable
fun BackendStatusIcon(
  status: BackendStatus,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
) {
  val icon =
    when (status) {
      BackendStatus.Checking -> Icons.Rounded.Cloud
      BackendStatus.Available -> Icons.Rounded.CloudDone
      BackendStatus.Unavailable -> Icons.Rounded.CloudOff
    }
  val description =
    when (status) {
      BackendStatus.Checking -> "Checking backend availability"
      BackendStatus.Available -> "Backend available"
      BackendStatus.Unavailable -> "Backend unavailable"
    }
  val tint =
    when (status) {
      BackendStatus.Checking -> ShuuenUi.Dim
      BackendStatus.Available -> ShuuenUi.Correct
      BackendStatus.Unavailable -> ShuuenUi.Muted
    }

  Icon(
    imageVector = icon,
    contentDescription = description,
    tint = tint,
    modifier = modifier.size(size),
  )
}

fun BackendStatus.label(): String =
  when (this) {
    BackendStatus.Checking -> "Checking"
    BackendStatus.Available -> "Available"
    BackendStatus.Unavailable -> "Unavailable"
  }
