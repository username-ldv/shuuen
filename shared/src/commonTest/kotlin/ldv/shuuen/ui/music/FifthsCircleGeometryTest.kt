package ldv.shuuen.ui.music

import kotlin.math.PI
import kotlin.math.abs
import ldv.shuuen.core.ui.components.music.inputs.accidentalPrefixLeftCardinalNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalPrefixLeftDiagonalRadiusNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalPrefixParts
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixRightCardinalNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixRightDiagonalRadiusNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixRightLowerDiagonalRadiusNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixRightSideRadiusNudgePx
import ldv.shuuen.core.ui.components.music.inputs.accidentalSuffixParts
import ldv.shuuen.core.ui.components.music.inputs.cardinalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleRingRadiusPx
import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleLabelRadiusPx
import ldv.shuuen.core.ui.components.music.inputs.leftCardinalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.leftDiagonalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.radialHalfExtentPx
import ldv.shuuen.core.ui.components.music.inputs.rightCardinalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.rightDiagonalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.rightLowerDiagonalAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.rightSideAxisFactor
import ldv.shuuen.core.ui.components.music.inputs.verticalCardinalAxisFactor
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
  fun labelRadiusTucksOnlyCardinalLabelsCloser() {
    val ringRadius = 200f
    val width = 20f
    val height = 30f
    val dotRadius = 10f

    val cardinalUntucked = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = width,
      labelHeightPx = height,
      angleRadians = 0.0,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
      cardinalTuckPx = 0f,
    )
    val cardinalTucked = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = width,
      labelHeightPx = height,
      angleRadians = 0.0,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
      cardinalTuckPx = 2f,
    )
    val diagonalUntucked = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = width,
      labelHeightPx = height,
      angleRadians = PI / 6.0,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
      cardinalTuckPx = 0f,
    )
    val diagonalTucked = fifthsCircleLabelRadiusPx(
      ringRadiusPx = ringRadius,
      minimumInsetPx = 24f,
      labelWidthPx = width,
      labelHeightPx = height,
      angleRadians = PI / 6.0,
      dotRadiusPx = dotRadius,
      dotGapPx = 0f,
      cardinalTuckPx = 2f,
    )

    assertEquals(2f, cardinalTucked - cardinalUntucked)
    assertEquals(diagonalUntucked, diagonalTucked)
  }

  @Test
  fun cardinalFactorsOnlyApplyNearAxes() {
    assertEquals(1f, cardinalAxisFactor(0.0))
    assertEquals(1f, cardinalAxisFactor(PI / 2.0))
    assertEquals(0f, cardinalAxisFactor(PI / 6.0))
    assertEquals(1f, verticalCardinalAxisFactor(PI / 2.0))
    assertEquals(0f, verticalCardinalAxisFactor(0.0))
    assertEquals(1f, rightCardinalAxisFactor(0.0))
    assertEquals(0f, rightCardinalAxisFactor(PI))
    assertEquals(1f, leftCardinalAxisFactor(PI))
    assertEquals(0f, leftCardinalAxisFactor(0.0))
  }

  @Test
  fun accidentalPrefixLeftNudgeIncludesFlatAndSharpSpellings() {
    assertEquals(-8f, accidentalPrefixLeftCardinalNudgePx("♭", 8f))
    assertEquals(-8f, accidentalPrefixLeftCardinalNudgePx("♯", 8f))
    assertEquals(-8f, accidentalPrefixLeftCardinalNudgePx("#", 8f))
    assertEquals(-8f, accidentalPrefixLeftCardinalNudgePx("b", 8f))
    assertEquals(-8f, accidentalPrefixLeftCardinalNudgePx("B", 8f))
    assertEquals(0f, accidentalPrefixLeftCardinalNudgePx("x", 8f))
  }

  @Test
  fun accidentalPrefixLeftNudgeOnlyAppliesWherePrefixFacesRing() {
    val nudge = accidentalPrefixLeftCardinalNudgePx("♭", 10f)

    assertEquals(-10f, nudge * leftCardinalAxisFactor(PI))
    assertTrue(abs(nudge * leftCardinalAxisFactor(0.0)) < 0.001f)
    assertTrue(abs(nudge * leftCardinalAxisFactor(PI / 2.0)) < 0.001f)
    assertTrue(abs(nudge * leftCardinalAxisFactor(5.0 * PI / 6.0)) < 0.001f)
  }

  @Test
  fun accidentalPrefixLeftDiagonalRadiusNudgeOnlyIncludesFlatSpellings() {
    assertEquals(4f, accidentalPrefixLeftDiagonalRadiusNudgePx("♭", 8f))
    assertEquals(4f, accidentalPrefixLeftDiagonalRadiusNudgePx("b", 8f))
    assertEquals(4f, accidentalPrefixLeftDiagonalRadiusNudgePx("B", 8f))
    assertEquals(0f, accidentalPrefixLeftDiagonalRadiusNudgePx("♯", 8f))
    assertEquals(0f, accidentalPrefixLeftDiagonalRadiusNudgePx("#", 8f))
    assertEquals(0f, accidentalPrefixLeftDiagonalRadiusNudgePx("x", 8f))
  }

  @Test
  fun leftDiagonalFactorOnlyAppliesToTwoLeftDiagonalSlots() {
    assertEquals(1f, leftDiagonalAxisFactor(5.0 * PI / 6.0))
    assertEquals(1f, leftDiagonalAxisFactor(7.0 * PI / 6.0))
    assertEquals(0f, leftDiagonalAxisFactor(PI))
    assertEquals(0f, leftDiagonalAxisFactor(2.0 * PI / 3.0))
    assertEquals(0f, leftDiagonalAxisFactor(4.0 * PI / 3.0))
    assertEquals(0f, leftDiagonalAxisFactor(0.0))
  }

  @Test
  fun accidentalSuffixRightNudgeIncludesFlatSpellings() {
    assertEquals(4f, accidentalSuffixRightCardinalNudgePx("♭", 8f))
    assertEquals(4f, accidentalSuffixRightCardinalNudgePx("b", 8f))
    assertEquals(4f, accidentalSuffixRightCardinalNudgePx("B", 8f))
    assertEquals(4f, accidentalSuffixRightCardinalNudgePx("♯", 8f))
    assertEquals(4f, accidentalSuffixRightCardinalNudgePx("#", 8f))
    assertEquals(0f, accidentalSuffixRightCardinalNudgePx("x", 8f))
  }

  @Test
  fun accidentalSuffixRightNudgeOnlyAppliesWhereSuffixFacesRing() {
    val nudge = accidentalSuffixRightCardinalNudgePx("♭", 10f)

    assertEquals(5f, nudge * rightCardinalAxisFactor(0.0))
    assertTrue(abs(nudge * rightCardinalAxisFactor(PI)) < 0.001f)
    assertTrue(abs(nudge * rightCardinalAxisFactor(PI / 2.0)) < 0.001f)
    assertTrue(abs(nudge * rightCardinalAxisFactor(PI / 6.0)) < 0.001f)
  }

  @Test
  fun accidentalSuffixRightSideRadiusNudgeIncludesFlatsAndSharps() {
    assertEquals(4f, accidentalSuffixRightSideRadiusNudgePx("♭", 8f))
    assertEquals(4f, accidentalSuffixRightSideRadiusNudgePx("b", 8f))
    assertEquals(4f, accidentalSuffixRightSideRadiusNudgePx("B", 8f))
    assertEquals(4f, accidentalSuffixRightSideRadiusNudgePx("♯", 8f))
    assertEquals(4f, accidentalSuffixRightSideRadiusNudgePx("#", 8f))
    assertEquals(0f, accidentalSuffixRightSideRadiusNudgePx("x", 8f))
  }

  @Test
  fun rightSideFactorOnlyAppliesToRightHalf() {
    assertEquals(1f, rightSideAxisFactor(0.0))
    assertTrue(abs(rightSideAxisFactor(PI / 3.0) - 0.5f) < 0.001f)
    assertTrue(abs(rightSideAxisFactor(-PI / 3.0) - 0.5f) < 0.001f)
    assertTrue(abs(rightSideAxisFactor(PI / 2.0)) < 0.001f)
    assertTrue(abs(rightSideAxisFactor(-PI / 2.0)) < 0.001f)
    assertTrue(abs(rightSideAxisFactor(PI)) < 0.001f)
  }

  @Test
  fun accidentalSuffixRightDiagonalRadiusNudgeIncludesFlatsAndSharps() {
    assertEquals(4f, accidentalSuffixRightDiagonalRadiusNudgePx("♭", 8f))
    assertEquals(4f, accidentalSuffixRightDiagonalRadiusNudgePx("b", 8f))
    assertEquals(4f, accidentalSuffixRightDiagonalRadiusNudgePx("B", 8f))
    assertEquals(4f, accidentalSuffixRightDiagonalRadiusNudgePx("♯", 8f))
    assertEquals(4f, accidentalSuffixRightDiagonalRadiusNudgePx("#", 8f))
    assertEquals(0f, accidentalSuffixRightDiagonalRadiusNudgePx("x", 8f))
  }

  @Test
  fun rightDiagonalFactorOnlyAppliesToTwoRightDiagonalSlots() {
    assertEquals(1f, rightDiagonalAxisFactor(PI / 6.0))
    assertEquals(1f, rightDiagonalAxisFactor(-PI / 6.0))
    assertEquals(0f, rightDiagonalAxisFactor(0.0))
    assertEquals(0f, rightDiagonalAxisFactor(PI / 3.0))
    assertEquals(0f, rightDiagonalAxisFactor(-PI / 3.0))
    assertEquals(0f, rightDiagonalAxisFactor(PI))
  }

  @Test
  fun accidentalSuffixRightLowerDiagonalRadiusNudgeIncludesFlatsAndSharps() {
    assertEquals(4f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("♭", 8f))
    assertEquals(4f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("b", 8f))
    assertEquals(4f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("B", 8f))
    assertEquals(4f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("♯", 8f))
    assertEquals(4f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("#", 8f))
    assertEquals(0f, accidentalSuffixRightLowerDiagonalRadiusNudgePx("x", 8f))
  }

  @Test
  fun rightLowerDiagonalFactorOnlyAppliesToVisual150DegreeSlot() {
    assertEquals(1f, rightLowerDiagonalAxisFactor(PI / 3.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(PI / 6.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(-PI / 6.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(0.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(PI / 2.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(-PI / 3.0))
    assertEquals(0f, rightLowerDiagonalAxisFactor(PI))
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
