package ldv.shuuen

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import ldv.shuuen.app.App
import ldv.shuuen.logging.Utf8StdoutHandler
import kotlin.time.Duration.Companion.milliseconds

fun main() {
  Napier.base(DebugAntilog(handler = listOf(Utf8StdoutHandler())))

  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "Shuuen",
      state = rememberWindowState(width = 1200.dp, height = 800.dp, placement = WindowPlacement.Maximized),
    ) {
      LaunchedEffect(Unit) {
        window.isAlwaysOnTop = true
        delay(200.milliseconds)
        window.isAlwaysOnTop = false
      }
      App()
    }
  }
}
