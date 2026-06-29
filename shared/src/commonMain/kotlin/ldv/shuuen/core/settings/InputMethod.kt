package ldv.shuuen.core.settings

import kotlinx.serialization.Serializable

/** Which on-screen input component is shown for answering. */
@Serializable
enum class InputComponent {
  Piano,
  Circle,
}

/**
 * How a tapped item is interpreted into a pitch.
 *
 * [Absolute] reads the item index as a chromatic pitch ordinal (C = 0 … B = 11).
 * [Relative] reads the item index as a chromatic degree offset from the current root.
 */
@Serializable
enum class InputMode {
  Absolute,
  Relative,
}

@Serializable
data class InputMethod(
  val component: InputComponent = InputComponent.Piano,
  val mode: InputMode = InputMode.Absolute,
  /**
   * Circle + [InputMode.Absolute] only. false = fixed layout (C at the top); true = the circle is
   * rotated so the current root sits at the top, with the other absolute labels following around it.
   * Ignored for every other component/mode (relative already keeps the tonic "1" at the top).
   */
  val circleAbsoluteRootAtTop: Boolean = false,
)
