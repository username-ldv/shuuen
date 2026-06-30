package ldv.shuuen.core.util

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