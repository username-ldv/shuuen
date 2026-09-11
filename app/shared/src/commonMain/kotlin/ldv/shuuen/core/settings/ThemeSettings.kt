package ldv.shuuen.core.settings

import kotlinx.serialization.Serializable

/**
 * Visual style of the app. Every style ships a dark and a light variant;
 * [ThemeSettings.appearance] decides which of the two is active.
 * Future non-monochrome themes are added as new entries here.
 */
@Serializable
enum class ThemeStyle {
  /** The original high-contrast monochrome look (pure black / pure white). */
  Mono,

  /** Lower-contrast monochrome: blacks lifted towards grey, whites dimmed towards grey. */
  MonoSoft,
}

/** Which brightness variant of the chosen [ThemeStyle] is shown. */
@Serializable
enum class ThemeAppearance {
  /** Follow the device's dark/light mode. */
  System,
  Dark,
  Light,
}

@Serializable
data class ThemeSettings(
  val style: ThemeStyle = ThemeStyle.Mono,
  val appearance: ThemeAppearance = ThemeAppearance.System,
)
