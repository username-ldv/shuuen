package ldv.shuuen.features.training.course.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import ldv.shuuen.features.training.common.TrainingFlow

class LevelReferenceTest {
  @Test
  fun localIdsRemainUnchanged() {
    val decoded = assertIs<LevelReference.Local>(LevelReference.decode("018f-local-uuid"))
    assertEquals("018f-local-uuid", decoded.id)
    assertEquals("018f-local-uuid", decoded.encoded)
  }

  @Test
  fun remoteReferencesRoundTripArbitraryUtf8LevelIds() {
    val original = LevelReference.Remote(42, TrainingFlow.Melodies, "level: C♯ / 60")
    val decoded = assertIs<LevelReference.Remote>(LevelReference.decode(original.encoded))

    assertEquals(original, decoded)
    assertEquals(original.encoded, decoded.encoded)
  }

  @Test
  fun malformedRemoteReferencesAreNotMistakenForLocalIds() {
    assertFailsWith<IllegalArgumentException> { LevelReference.decode("course:not-an-id:melodies:00") }
  }

  @Test
  fun theSameBackendLevelIdInDifferentCoursesHasDifferentStatsKeys() {
    val first = LevelReference.Remote(1, TrainingFlow.Melodies, "level").encoded
    val second = LevelReference.Remote(2, TrainingFlow.Melodies, "level").encoded

    kotlin.test.assertNotEquals(first, second)
  }
}
