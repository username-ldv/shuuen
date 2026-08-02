package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor
import org.koin.compose.koinInject

/**
 * App-bar entry point to the optional backend account. Renders nothing until the backend has
 * actually answered — there is nothing to sign in to otherwise. Meant for the [ShuuenTopAppBar]
 * status slot, beside [BackendStatusBadge].
 */
@Composable
fun AccountBadge(
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val backendStatusMonitor = koinInject<BackendStatusMonitor>()
  val authRepository = koinInject<AuthRepository>()
  val status by backendStatusMonitor.status.collectAsStateWithLifecycle()
  val session by authRepository.session.collectAsStateWithLifecycle()
  if (status != BackendStatus.Available) return

  val signedInAs = session?.user?.label
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
      imageVector = if (signedInAs != null) Icons.Rounded.AccountCircle else Icons.Rounded.Person,
      contentDescription = signedInAs?.let { "Signed in as $it" } ?: "Sign in",
      tint = if (signedInAs != null) ShuuenUi.Correct else ShuuenUi.Muted,
      modifier = Modifier.size(20.dp),
    )
  }
}
