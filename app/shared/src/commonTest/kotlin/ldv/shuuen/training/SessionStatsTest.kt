package ldv.shuuen.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ldv.shuuen.features.training.level_end.domain.QuestionResult
import ldv.shuuen.features.training.level_end.domain.accuracyBuckets
import ldv.shuuen.features.training.level_end.domain.longestCleanRun

class SessionStatsTest {
  @Test
  fun findsLongestCleanRun() {
    assertEquals(0, longestCleanRun(0, emptySet()))
    assertEquals(5, longestCleanRun(5, emptySet()))
    assertEquals(0, longestCleanRun(3, setOf(0, 1, 2)))
    // misses at 2 and 6 split 0..9 into runs of 2, 3, and 3.
    assertEquals(3, longestCleanRun(10, setOf(2, 6)))
    // trailing run is the longest.
    assertEquals(7, longestCleanRun(10, setOf(2)))
  }

  @Test
  fun skipsBucketsForShortSessions() {
    val results = List(7) { QuestionResult(it + 1, 1, 0) }
    assertTrue(accuracyBuckets(results).isEmpty())
  }

  @Test
  fun bucketsEvenSessions() {
    // 20 questions, misses in questions 1..5 only.
    val results = List(20) { i -> QuestionResult(i + 1, 1, if (i < 5) 1 else 0) }
    val buckets = accuracyBuckets(results)
    assertEquals(4, buckets.size)
    assertEquals("1-5", buckets[0].rangeLabel)
    assertEquals("16-20", buckets[3].rangeLabel)
    assertEquals(0f, buckets[0].accuracy)
    assertEquals(1f, buckets[3].accuracy)
  }

  @Test
  fun bucketsUnevenSessionsWithoutLosingQuestions() {
    val results = List(10) { i -> QuestionResult(i + 1, 1, 0) }
    val buckets = accuracyBuckets(results)
    assertEquals(4, buckets.size)
    // Sizes 3, 3, 2, 2 — every question lands in exactly one bucket.
    assertEquals(1, buckets[0].firstQuestion)
    assertEquals(10, buckets[3].lastQuestion)
    for (i in 1 until buckets.size) {
      assertEquals(buckets[i - 1].lastQuestion + 1, buckets[i].firstQuestion)
    }
  }

  @Test
  fun weighsBucketAccuracyByNotes() {
    // Multi-note sequences: 8 questions of 4 notes, one miss per question in the first half.
    val results = List(8) { i -> QuestionResult(i + 1, 4, if (i < 4) 1 else 0) }
    val buckets = accuracyBuckets(results)
    assertEquals(4, buckets.size)
    assertEquals(0.75f, buckets[0].accuracy)
    assertEquals(1f, buckets[2].accuracy)
  }
}
