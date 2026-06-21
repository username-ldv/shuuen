package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboard

@Composable
fun RangeKeyboardStrip(
  modifier: Modifier = Modifier.Companion,
  firstSelected: Int = 12,
  lastSelectedExclusive: Int = 37,
) {
  val keyCount = 12
  val selectedRange = firstSelected until lastSelectedExclusive
  ldv.shuuen.core.ui.components.music.inputs.PianoKeyboard(
    modifier = Modifier.fillMaxWidth().aspectRatio(
      ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults.aspectRatio(
        keyCount
      )
    ),
    keyCount = keyCount,
//      idleKeyColors = List(keyCount) { index ->
//        when {
//          index !in selectedRange -> if (PianoKeyboardDefaults.isBlackKey(index)) Color(0xFF151515) else Color(
//            0xFF292929
//          )
//
//          PianoKeyboardDefaults.isBlackKey(index) -> Color(0xFF34313F)
//          else -> Color(0xFFE2D8FF)
//        }
//      },
  )
//    RangeHandle(Modifier.align(Alignment.CenterStart).padding(start = 0.dp))
//    RangeHandle(Modifier.align(Alignment.CenterEnd).padding(end = 86.dp))
}