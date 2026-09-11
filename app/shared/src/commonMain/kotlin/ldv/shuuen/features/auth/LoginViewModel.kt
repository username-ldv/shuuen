package ldv.shuuen.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ldv.shuuen.core.auth.AuthException
import ldv.shuuen.core.auth.AuthFailure
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor
import ldv.shuuen.data.remote.ApiConfig

data class LoginUiState(
  val backendUrl: String = "",
  val backendStatus: BackendStatus = BackendStatus.Checking,
  val session: AuthSession? = null,
  val username: String = "",
  val password: String = "",
  val passwordVisible: Boolean = false,
  val submitting: Boolean = false,
  val errorMessage: String? = null,
) {
  val signedIn: Boolean
    get() = session != null

  val canSubmit: Boolean
    get() = !submitting && username.isNotBlank() && password.isNotEmpty()
}

sealed interface LoginAction {
  data class SetUsername(val value: String) : LoginAction
  data class SetPassword(val value: String) : LoginAction
  data object TogglePasswordVisibility : LoginAction
  data object SignIn : LoginAction
  data object SignOut : LoginAction
}

class LoginViewModel(
  private val authRepository: AuthRepository,
  private val backendStatusMonitor: BackendStatusMonitor,
  apiConfig: ApiConfig,
) : ViewModel() {
  private val mutableState = MutableStateFlow(LoginUiState())
  val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

  private var submitJob: Job? = null

  init {
    viewModelScope.launch {
      authRepository.session.collect { session ->
        // Signing in or out elsewhere (a rejected stored token, say) resets the form either way.
        mutableState.update {
          it.copy(session = session, password = "", passwordVisible = false)
        }
      }
    }
    viewModelScope.launch {
      backendStatusMonitor.status.collect { status ->
        mutableState.update { it.copy(backendStatus = status) }
      }
    }
    viewModelScope.launch {
      apiConfig.baseUrl.collect { url -> mutableState.update { it.copy(backendUrl = url) } }
    }
  }

  fun onAction(action: LoginAction) {
    when (action) {
      is LoginAction.SetUsername ->
        mutableState.update { it.copy(username = action.value, errorMessage = null) }

      is LoginAction.SetPassword ->
        mutableState.update { it.copy(password = action.value, errorMessage = null) }

      LoginAction.TogglePasswordVisibility ->
        mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }

      LoginAction.SignIn -> signIn()

      LoginAction.SignOut -> signOut()
    }
  }

  private fun signIn() {
    val current = mutableState.value
    if (!current.canSubmit) return

    submitJob?.cancel()
    mutableState.update { it.copy(submitting = true, errorMessage = null) }
    submitJob = viewModelScope.launch {
      try {
        authRepository.signIn(current.username, current.password)
        // The session collector clears the password; only the pending flag is ours to drop.
        mutableState.update { it.copy(submitting = false, errorMessage = null) }
      } catch (error: CancellationException) {
        throw error
      } catch (error: AuthException) {
        mutableState.update { it.copy(submitting = false, errorMessage = error.message) }
        // Only a failure to reach the backend says anything new about its status. Re-checking
        // after every attempt would flip the badge through Checking and churn the whole screen.
        if (error.failure == AuthFailure.Unreachable) backendStatusMonitor.refresh()
      } catch (error: Throwable) {
        Napier.w(error) { "Signing in failed unexpectedly" }
        mutableState.update {
          it.copy(submitting = false, errorMessage = "Signing in failed. Try again.")
        }
      }
    }
  }

  private fun signOut() {
    submitJob?.cancel()
    submitJob = viewModelScope.launch {
      authRepository.signOut()
      mutableState.update {
        it.copy(username = "", password = "", submitting = false, errorMessage = null)
      }
    }
  }
}
