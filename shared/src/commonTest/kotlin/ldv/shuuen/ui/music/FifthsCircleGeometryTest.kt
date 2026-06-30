package ldv.shuuen.ui.music

import kotlin.math.PI
import kotlin.math.abs
import ldv.shuuen.core.ui.components.music.inputs.accidentalPrefixParts
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixParts
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleRingRadiusPx
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleLabelRadiusPx
import ldv.shuuen.core.ui.components.music.inputs.radialHalfExtentPx
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
      dotRadiusPx = 10f,
      dotGapPx = 8f,
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
        dotRadiusPx = 0f,
        dotGapPx = 0f,
      ),
    )
  }

  @Test
  fun labelRadiusKeepsConsistentGapFromDotsForDifferentWidths() {
    val ringRadius = 200f
    val dotRadius = 10f
    val dotGap = 8f
    val narrowHalfWidth = 12f
    val wideHalfWidth = 24f

    val narrowRadius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = narrowHalfWidth * 2f,
      labelHeightPx = 30f,
      angleRadians = 0.0,
      dotRadiusPx = dotRadius,
      dotGapPx = dotGap,
    )
    val wideRadius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = wideHalfWidth * 2f,
      labelHeightPx = 30f,
      angleRadians = PI,
      dotRadiusPx = dotRadius,
      dotGapPx = dotGap,
    )

    assertEquals(dotGap, ringRadius - dotRadius - narrowRadius - narrowHalfWidth)
    assertEquals(dotGap, ringRadius - dotRadius - wideRadius - wideHalfWidth)
  }

  @Test
  fun labelRadiusKeepsConsistentGapWithZeroConfiguredGap() {
    val ringRadius = 200f
    val dotRadius = 10f
    val narrowHalfWidth = 7f
    val wideHalfWidth = 17f

    val narrowRadius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = narrowHalfWidth * 2f,
      labelHeightPx = 30f,
      angleRadians = 0.0,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
    )
    val wideRadius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = wideHalfWidth * 2f,
      labelHeightPx = 30f,
      angleRadians = PI,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
    )

    assertEquals(0f, ringRadius - dotRadius - narrowRadius - narrowHalfWidth)
    assertEquals(0f, ringRadius - dotRadius - wideRadius - wideHalfWidth)
  }

  @Test
  fun labelRadiusUsesOpticalExtentForDiagonalLabels() {
    val ringRadius = 200f
    val dotRadius = 10f
    val dotGap = 8f
    val width = 44f
    val height = 30f
    val angle = 2.0 * PI / 3.0

    val radialHalfExtent = radialHalfExtentPx(width, height, angle)
    val rectangleRayHalfExtent = height / (2f * kotlin.math.sin(angle).toFloat())
    val radius = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = width,
      labelHeightPx = height,
      angleRadians = angle,
      dotRadiusPx = dotRadius,
      dotGapPx = dotGap,
    )

    assertTrue(radialHalfExtent < rectangleRayHalfExtent)
    assertTrue(abs(dotGap - (ringRadius - dotRadius - radius - radialHalfExtent)) < 0.001f)
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

  @Test
  fun accidentalPrefixOnlySplitsAccidentalLabels() {
    assertEquals("♭" to "2", accidentalPrefixParts("♭2"))
    assertEquals("♯" to "4", accidentalPrefixParts("♯4"))
    assertEquals("#" to "4", accidentalPrefixParts("#4"))
    assertEquals("b" to "2", accidentalPrefixParts("b2"))
    assertNull(accidentalPrefixParts("Yo"))
    assertNull(accidentalPrefixParts("Bob"))
    assertNull(accidentalPrefixParts("D♭"))
  }
}
