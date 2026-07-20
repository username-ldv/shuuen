package ldv.shuuen.core.ui.theme

import androidx.compose.runtime.Composable

/**
 * Keeps the platform system bars (status/navigation icon brightness) in sync
 * with the active theme variant, so forcing Dark/Light in the app doesn't
 * leave unreadable bar icons from the device's own mode. No-op on platforms
 * without system bars.
 */
@Composable
internal expect fun SyncSystemBarsWithTheme(darkTheme: Boolean)
