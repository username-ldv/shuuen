package ldv.shuuen.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.ui.components.BackendStatusBadge
import ldv.shuuen.core.ui.components.BackendStatusIcon
import ldv.shuuen.core.ui.components.FlatSection
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.PillControl
import ldv.shuuen.core.ui.components.PrimaryCta
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.label

@Composable
fun LoginScreen(
  viewModel: LoginViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        title = "ACCOUNT",
        onBack = onNavigateBack,
        statusContent = { BackendStatusBadge() },
        type = ShuuenTopAppBarType.Simple,
      )
    },
  ) {
    // Each section takes only the values it draws, so typing a password doesn't recompose the
    // backend row and a status change doesn't recompose the form.
    BackendRow(url = state.backendUrl, status = state.backendStatus)
    Hairline()

    val session = state.session
    if (session != null) {
      SignedInSection(session = session, onAction = viewModel::onAction)
    } else {
      SignInSection(
        username = state.username,
        password = state.password,
        passwordVisible = state.passwordVisible,
        errorMessage = state.errorMessage,
        canSubmit = state.canSubmit,
        onAction = viewModel::onAction,
      )
    }
  }
}

@Composable
private fun BackendRow(url: String, status: BackendStatus) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    BackendStatusIcon(status, size = 22.dp)
    Column(modifier = Modifier.weight(1f)) {
      Text(text = "Backend", color = ShuuenUi.Text, style = MaterialTheme.typography.titleMedium)
      Text(
        text = url,
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = status.label().uppercase(),
      color = if (status == BackendStatus.Available) ShuuenUi.Correct else ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = ShuuenUi.labelSpacing),
    )
  }
}

@Composable
private fun SignedInSection(
  session: AuthSession,
  onAction: (LoginAction) -> Unit,
) {
  FlatSection(label = "SIGNED IN") {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        Icons.Rounded.AccountCircle,
        contentDescription = null,
        tint = ShuuenUi.Correct,
        modifier = Modifier.size(30.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = session.user.label,
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = accountSubtitle(session),
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      PillControl(
        text = "SIGN OUT",
        leadingIcon = Icons.Rounded.Close,
        fillLabel = false,
        onClick = { onAction(LoginAction.SignOut) },
      )
    }
    Text(
      text = "Signing out only forgets the session on this device.",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

private fun accountSubtitle(session: AuthSession): String {
  val handle = "@${session.user.username}"
  return if (session.user.isAdmin) "$handle · administrator" else handle
}

/**
 * The fields stay enabled and the button keeps its label while a request is in flight. Disabling
 * them mid-submit greys the form and drops keyboard focus, which reads as the screen resetting;
 * [canSubmit] is already false while submitting, so the button is inert either way.
 */
@Composable
private fun SignInSection(
  username: String,
  password: String,
  passwordVisible: Boolean,
  errorMessage: String?,
  canSubmit: Boolean,
  onAction: (LoginAction) -> Unit,
) {
  FlatSection(
    label = "SIGN IN",
    supporting =
      "Optional. Training works signed out — an account only adds what the backend keeps " +
        "behind one.",
  ) {
    OutlinedTextField(
      value = username,
      onValueChange = { onAction(LoginAction.SetUsername(it)) },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Username") },
      leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
      singleLine = true,
      isError = errorMessage != null,
      keyboardOptions =
        KeyboardOptions(
          keyboardType = KeyboardType.Text,
          capitalization = KeyboardCapitalization.None,
          imeAction = ImeAction.Next,
        ),
    )

    OutlinedTextField(
      value = password,
      onValueChange = { onAction(LoginAction.SetPassword(it)) },
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Password") },
      singleLine = true,
      isError = errorMessage != null,
      visualTransformation =
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      trailingIcon = {
        IconButton(onClick = { onAction(LoginAction.TogglePasswordVisibility) }) {
          Icon(
            imageVector =
              if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (passwordVisible) "Hide password" else "Show password",
          )
        }
      },
    )

    if (errorMessage != null) {
      Text(
        text = errorMessage,
        color = ShuuenUi.Incorrect,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    PrimaryCta(
      text = "SIGN IN",
      icon = Icons.Rounded.Person,
      onClick = { onAction(LoginAction.SignIn) },
      modifier = Modifier.alpha(if (canSubmit) 1f else 0.38f),
    )

    Text(
      text = "Accounts are created on the backend; the app can't register new ones yet.",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
  }
}
