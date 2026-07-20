package ldv.shuuen.core.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun SyncSystemBarsWithTheme(darkTheme: Boolean) {
  val context = LocalContext.current
  val activity = remember(context) { context.findComponentActivity() } ?: return
  LaunchedEffect(activity, darkTheme) {
    // SystemBarStyle.dark = dark bar background -> light icons, and vice versa.
    val style =
      if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
      else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
  }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
  }
