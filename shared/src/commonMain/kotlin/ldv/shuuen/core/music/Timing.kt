package ldv.shuuen.core.music

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class Timing(val tempo: Int) {
  init {
    require(tempo > 0) { "Tempo must be positive." }
  }

  fun whole(): Duration = quarter() * 4.0

  fun half(): Duration = quarter() * 2.0

  fun quarter(): Duration = (60_000.0 / tempo).milliseconds

  fun eighth(): Duration = quarter() / 2.0

  fun sixteenth(): Duration = quarter() / 4.0

  fun thirtySecond(): Duration = quarter() / 8.0

  fun sixtyFourth(): Duration = quarter() / 16.0

  fun oneHundredTwentyEighth(): Duration = quarter() / 32.0
}

inline fun <T> withTiming(tempo: Int, block: Timing.() -> T): T = Timing(tempo).block()
