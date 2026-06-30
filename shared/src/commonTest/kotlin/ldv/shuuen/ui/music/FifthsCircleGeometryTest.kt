package ldv.shuuen.ui.music

import kotlin.math.PI
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixParts
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleRingRadiusPx
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleLabelRadiusPx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FifthsCircleGeometryTest {
  @Test
  fun ringRadiusUsesDotEdgePaddingWhenProvided() {
    assertEquals(
      93f,
      fifthsCircleRingRadiusPx(
        minSidePx = 200f,
        outerPaddingPx = 30f,
        dotRadiusPx = 7f,
        dotEdgePaddingPx = 0f,
      ),
    )
  }

  @Test
  fun ringRadiusFallsBackToOuterPadding() {
    assertEquals(
      70f,
      fifthsCircleRingRadiusPx(
        minSidePx = 200f,
        outerPaddingPx = 30f,
        dotRadiusPx = 7f,
        dotEdgePaddingPx = null,
      ),
    )
  }

  @Test
  fun labelRadiusMovesWideDiagonalLabelsInward() {
    val radius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = 200f,
      minimumInsetPx = 24f,
      labelWidthPx = 44f,
      labelHeightPx = 30f,
      angleRadians = 5.0 * PI / 6.0,
      ringGapPx = 8f,
    )

    assertTrue(radius < 176f)
  }

  @Test
  fun labelRadiusKeepsMinimumInsetWhenTextAlreadyFits() {
    assertEquals(
      176f,
      fifthsCircleLabelRadiusPx(
        ringRadiusPx = 200f,
        minimumInsetPx = 24f,
        labelWidthPx = 20f,
        labelHeightPx = 28f,
        angleRadians = -PI / 2.0,
        ringGapPx = 8f,
      ),
    )
  }

  @Test
  fun accidentalSuffixOnlySplitsAccidentalLabels() {
    assertEquals("D" to "♭", accidentalSuffixParts("D♭"))
    assertEquals("F" to "#", accidentalSuffixParts("F#"))
    assertEquals("A" to "b", accidentalSuffixParts("Ab"))
    assertEquals("Yo" to "♯", accidentalSuffixParts("Yo♯"))
    assertNull(accidentalSuffixParts("Yo"))
    assertNull(accidentalSuffixParts("Bob"))
    assertNull(accidentalSuffixParts("♭2"))
  }
}
