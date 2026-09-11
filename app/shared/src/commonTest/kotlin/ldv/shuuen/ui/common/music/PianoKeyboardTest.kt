package ldv.shuuen.core.ui.components.music

import androidx.compose.ui.geometry.Offset
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyGeometry
import ldv.shuuen.core.ui.components.music.inputs.buildPianoKeyGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class PianoKeyboardTest {
  @Test
  fun blackKeyHitboxesFillVisualGapAndChooseClosestKey() {
    val geometry = testGeometry()

    assertEquals(1, geometry.hitTest(Offset(x = 145f, y = 20f)))
    assertEquals(3, geometry.hitTest(Offset(x = 155f, y = 20f)))
  }

  @Test
  fun blackKeyHitboxesDoNotExtendBelowBlackKeys() {
    val geometry = testGeometry()

    assertEquals(2, geometry.hitTest(Offset(x = 145f, y = 80f)))
  }

  @Test
  fun blackKeyHitboxExtendsIntoWideWhiteGap() {
    val geometry = testGeometry()

    assertEquals(3, geometry.hitTest(Offset(x = 249f, y = 20f)))
    assertEquals(4, geometry.hitTest(Offset(x = 299f, y = 20f)))
  }

  @Test
  fun edgeBlackKeyHitboxesExtendPastVisualKeyWidth() {
    val geometry = testGeometry()

    assertEquals(0, geometry.hitTest(Offset(x = 23f, y = 20f)))
    assertEquals(1, geometry.hitTest(Offset(x = 25f, y = 20f)))
    assertEquals(10, geometry.hitTest(Offset(x = 675f, y = 20f)))
    assertEquals(11, geometry.hitTest(Offset(x = 677f, y = 20f)))
  }

  @Test
  fun adjacentBlackKeyHitboxOverlapsChooseClosestKey() {
    val geometry = testGeometry()

    assertEquals(6, geometry.hitTest(Offset(x = 449f, y = 20f)))
    assertEquals(8, geometry.hitTest(Offset(x = 451f, y = 20f)))
    assertEquals(8, geometry.hitTest(Offset(x = 549f, y = 20f)))
    assertEquals(10, geometry.hitTest(Offset(x = 551f, y = 20f)))
  }

  private fun testGeometry(): List<PianoKeyGeometry> = buildPianoKeyGeometry(
    width = 700f,
    height = 100f,
    keyCount = 12,
    borderPx = 0f,
    blackKeyWidthFraction = 0.52f,
    blackKeyHeightFraction = 0.62f,
  )
}

private fun List<PianoKeyGeometry>.hitTest(position: Offset): Int? {
  val blackHit = asSequence()
    .filter { it.isBlack && it.hitRect.contains(position) }
    .minByOrNull { abs(position.x - it.rect.center.x) }

  if (blackHit != null) {
    return blackHit.index
  }

  return firstOrNull { !it.isBlack && it.rect.contains(position) }?.index
}
