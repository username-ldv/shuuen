package ldv.shuuen.features.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ldv.shuuen.core.auth.AuthException
import ldv.shuuen.core.auth.AuthFailure
import ldv.shuuen.core.auth.AuthRepository
import ldv.shuuen.core.auth.AuthSession
import ldv.shuuen.core.auth.AuthUser
import ldv.shuuen.core.online.BackendStatus
import ldv.shuuen.core.online.BackendStatusMonitor
import ldv.shuuen.data.remote.ApiConfig

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun aSuccessfulSignInShowsTheAccountAndForgetsThePassword() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    val viewModel = viewModel(auth)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("hunter2000"))
    assertTrue(viewModel.state.value.canSubmit)

    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals("Learner", assertNotNull(state.session).user.username)
    assertTrue(state.signedIn)
    assertEquals("", state.password)
    assertFalse(state.submitting)
    assertNull(state.errorMessage)
    assertEquals("Learner" to "hunter2000", auth.lastCredentials)
  }

  @Test
  fun aRejectedSignInKeepsTheFormAndExplainsWhy() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    auth.failure =
      AuthException(AuthFailure.InvalidCredentials, "Wrong username or password.")
    val viewModel = viewModel(auth)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("nope"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertNull(state.session)
    assertFalse(state.submitting)
    assertEquals("Wrong username or password.", state.errorMessage)
    assertEquals("Learner", state.username)
    assertEquals("nope", state.password)
  }

  @Test
  fun typingClearsTheStaleError() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    auth.failure = AuthException(AuthFailure.Server, "The backend couldn't sign you in.")
    val viewModel = viewModel(auth)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("hunter2000"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()
    assertNotNull(viewModel.state.value.errorMessage)

    viewModel.onAction(LoginAction.SetPassword("hunter2001"))

    assertNull(viewModel.state.value.errorMessage)
  }

  @Test
  fun anEmptyFormNeverReachesTheBackend() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    val viewModel = viewModel(auth)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("   "))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    assertFalse(viewModel.state.value.canSubmit)
    assertEquals(0, auth.signInCount)
  }

  @Test
  fun signingOutClearsTheSessionAndTheForm() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    val viewModel = viewModel(auth)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("hunter2000"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SignOut)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertFalse(state.signedIn)
    assertEquals("", state.username)
    assertEquals("", state.password)
  }

  @Test
  fun theScreenReportsTheConfiguredBackend() = runTest(dispatcher) {
    val viewModel = viewModel(FakeAuthRepository())
    advanceUntilIdle()

    assertEquals("http://backend.test", viewModel.state.value.backendUrl)
    assertEquals(BackendStatus.Available, viewModel.state.value.backendStatus)
  }

  /**
   * Re-checking after every attempt flips the badge through Checking and back, which churns the
   * whole screen for no new information — wrong credentials say nothing about reachability.
   */
  @Test
  fun aRejectedSignInLeavesTheBackendBadgeAlone() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    auth.failure = AuthException(AuthFailure.InvalidCredentials, "Wrong username or password.")
    val monitor = FakeBackendStatusMonitor()
    val viewModel = LoginViewModel(auth, monitor, ApiConfig("http://backend.test"))
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("nope"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    assertEquals(0, monitor.refreshCount)
  }

  @Test
  fun anUnreachableBackendRechecksTheStatus() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    auth.failure = AuthException(AuthFailure.Unreachable, "Couldn't reach http://backend.test.")
    val monitor = FakeBackendStatusMonitor()
    val viewModel = LoginViewModel(auth, monitor, ApiConfig("http://backend.test"))
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("hunter2000"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    assertEquals(1, monitor.refreshCount)
  }

  @Test
  fun aSuccessfulSignInLeavesTheBackendBadgeAlone() = runTest(dispatcher) {
    val auth = FakeAuthRepository()
    val monitor = FakeBackendStatusMonitor()
    val viewModel = LoginViewModel(auth, monitor, ApiConfig("http://backend.test"))
    advanceUntilIdle()

    viewModel.onAction(LoginAction.SetUsername("Learner"))
    viewModel.onAction(LoginAction.SetPassword("hunter2000"))
    viewModel.onAction(LoginAction.SignIn)
    advanceUntilIdle()

    assertEquals(0, monitor.refreshCount)
  }

  private fun viewModel(auth: FakeAuthRepository) =
    LoginViewModel(auth, FakeBackendStatusMonitor(), ApiConfig("http://backend.test"))
}

private class FakeAuthRepository : AuthRepository {
  private val mutableSession = MutableStateFlow<AuthSession?>(null)
  override val session = mutableSession.asStateFlow()

  var failure: AuthException? = null
  var signInCount = 0
  var lastCredentials: Pair<String, String>? = null

  override suspend fun signIn(username: String, password: String): AuthSession {
    signInCount += 1
    lastCredentials = username to password
    failure?.let { throw it }
    val signedIn =
      AuthSession(
        user = AuthUser(1, username, "", "user"),
        accessToken = "issued-token",
        backendUrl = "http://backend.test",
      )
    mutableSession.value = signedIn
    return signedIn
  }

  override suspend fun signOut() {
    mutableSession.value = null
  }
}

private class FakeBackendStatusMonitor : BackendStatusMonitor {
  private val mutableStatus = MutableStateFlow(BackendStatus.Available)
  override val status = mutableStatus.asStateFlow()

  var refreshCount = 0

  override fun refresh() {
    refreshCount += 1
  }
}
