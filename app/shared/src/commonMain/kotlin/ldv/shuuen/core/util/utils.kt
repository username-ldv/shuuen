package ldv.shuuen.core.util

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

inline fun <T> List<T>.updateBy(
  condition: (T) -> Boolean,
  by: (T) -> T
): List<T> {
  val index = indexOfFirst { condition(it) }

  return if (index == -1) {
    this
  } else {
    toMutableList().also { it[index] = by(it[index]) }
  }
}

val flatSharpRegex = Regex("[${Constants.LabelFlat}${Constants.LabelSharp}]")

fun Float.toRoundedString(decimals: Int): String {
  require(decimals >= 0)

  return DecimalFormat("#.${"#".repeat(decimals)}", DecimalFormatSymbols(Locale.US)).apply {
    roundingMode = RoundingMode.HALF_UP
    isGroupingUsed = false
  }.format(this)
}