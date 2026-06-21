package ldv.shuuen.ui.music

import ldv.shuuen.core.ui.components.music.inputs.fifthsCircleRingRadiusPx
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
