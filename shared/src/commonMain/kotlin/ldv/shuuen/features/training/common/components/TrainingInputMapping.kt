package ldv.shuuen.features.training.common.components

import androidx.compose.ui.graphics.Color
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.chromaticSpellingByOrdinal
import ldv.shuuen.core.music.customLabel
import ldv.shuuen.core.music.effectiveDegreeNames
import ldv.shuuen.core.settings.InputComponent
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings

/**
 * A request from a play VM for the screen to flash an input item (used for setup-melody
 * highlights). Carries the absolute [pitch]; the screen maps it to the active input component's
 * item index via [pitchToItemIndex].
 */
data class KeyFlashRequest(val pitch: Pitch, val color: Color)

/**
 * Maps an absolute [pitch] to the item index of the active input component. In [InputMode.Absolute]
 * the index is the chromatic pitch ordinal; in [InputMode.Relative] it is the chromatic degree
 * offset from [root] (falling back to the ordinal until a root is known).
 */
fun pitchToItemIndex(pitch: Pitch, mode: InputMode, root: Pitch?): Int =
    when (mode) {
      InputMode.Absolute -> pitch.ordinal
      InputMode.Relative -> root?.asRoot(pitch)?.offset ?: pitch.ordinal
    }

/**
 * Labels for the FifthsCircle items, indexed by item index (= pitch ordinal for absolute). Relative
 * uses configurable degree names; absolute uses key-aware spelling for the current
 * [root]/[accidentalType] (sharps vs flats, proper letters), falling back to C-major sharps before a
 * quiz state exists.
 */
fun circleItemNames(
    mode: InputMode,
    root: Pitch?,
    accidentalType: ScaleAccidentalType?,
    musicLabels: MusicLabelSettings,
): List<String> =
    when (mode) {
      InputMode.Relative -> effectiveDegreeNames(musicLabels.degreeNames)
      InputMode.Absolute ->
          chromaticSpellingByOrdinal(
                  rootOrdinal = root?.ordinal ?: 0,
                  accidentalType = accidentalType ?: ScaleAccidentalType.Sharps,
              )
              .map { it.customLabel(musicLabels.noteNames) }
    }

/**
 * The item the circle should rotate to its top slot, or null to keep the default top (C for
 * absolute, the tonic "1" for relative). Only Circle + Absolute with
 * [InputMethod.circleAbsoluteRootAtTop] pins the current [root] to the top; the circle animates the
 * spin itself whenever this changes (e.g. when a random-scale level rotates its root).
 */
fun circleTopItem(method: InputMethod, root: Pitch?): Int? =
    if (method.component == InputComponent.Circle &&
        method.mode == InputMode.Absolute &&
        method.circleAbsoluteRootAtTop) {
      root?.ordinal
    } else {
      null
    }
