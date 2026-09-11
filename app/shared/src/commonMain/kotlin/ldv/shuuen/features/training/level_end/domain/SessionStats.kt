package ldv.shuuen.features.training.level_end.domain

/**
 * Longest run of consecutive positions (chronological, 0-based) that are not in
 * [missedPositions]. Used for the best-streak stat: a position is a Singles question or a
 * melody note, in the order they were answered.
 */
fun longestCleanRun(totalPositions: Int, missedPositions: Set<Int>): Int {
  var best = 0
  var current = 0
  for (position in 0 until totalPositions) {
    if (position in missedPositions) {
      current = 0
    } else {
      current += 1
      if (current > best) best = current
    }
  }
  return best
}

/** One bar of the accuracy-by-range breakdown: which questions it spans and how they went. */
data class AccuracyBucket(
  val firstQuestion: Int,
  val lastQuestion: Int,
  /** 0..1 share of first-try-correct notes within the bucket. */
  val accuracy: Float,
) {
  val rangeLabel: String
    get() = if (firstQuestion == lastQuestion) "$firstQuestion" else "$firstQuestion-$lastQuestion"
}

/**
 * Splits the session's questions into up to [bucketCount] contiguous ranges and computes each
 * range's note accuracy. Returns an empty list when there is too little data for a breakdown to
 * mean anything (fewer than two questions per bucket).
 */
fun accuracyBuckets(results: List<QuestionResult>, bucketCount: Int = 4): List<AccuracyBucket> {
  if (results.size < bucketCount * 2) return emptyList()
  val sorted = results.sortedBy { it.questionNumber }
  val bucketSize = sorted.size / bucketCount
  val remainder = sorted.size % bucketCount
  val buckets = mutableListOf<AccuracyBucket>()
  var index = 0
  repeat(bucketCount) { bucket ->
    // Spread the remainder over the leading buckets so sizes differ by at most one.
    val size = bucketSize + if (bucket < remainder) 1 else 0
    val slice = sorted.subList(index, index + size)
    index += size
    val notes = slice.sumOf { it.noteCount }
    val missed = slice.sumOf { it.missedCount }
    val accuracy = if (notes > 0) (notes - missed).toFloat() / notes else 0f
    buckets +=
      AccuracyBucket(
        firstQuestion = slice.first().questionNumber,
        lastQuestion = slice.last().questionNumber,
        accuracy = accuracy.coerceIn(0f, 1f),
      )
  }
  return buckets
}
