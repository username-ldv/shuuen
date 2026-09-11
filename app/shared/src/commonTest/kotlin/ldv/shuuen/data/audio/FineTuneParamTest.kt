package ldv.shuuen.data.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class FineTuneParamTest {
  @Test
  fun zeroCentsIsInTune() {
    assertEquals(8192, fineTuneParam(0))
  }

  @Test
  fun mapsCentsLinearlyAcrossTheMidiRange() {
    assertEquals(13926, fineTuneParam(70))
    assertEquals(2458, fineTuneParam(-70))
  }

  @Test
  fun extremesClampToTheEventRange() {
    assertEquals(16383, fineTuneParam(100))
    assertEquals(0, fineTuneParam(-100))
    assertEquals(16383, fineTuneParam(250))
    assertEquals(0, fineTuneParam(-250))
  }
}
