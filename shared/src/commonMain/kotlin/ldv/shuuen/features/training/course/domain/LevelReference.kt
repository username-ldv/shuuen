package ldv.shuuen.features.training.course.domain

import ldv.shuuen.features.training.common.TrainingFlow

/** A navigation- and database-safe identity for either an existing Room level or a course level. */
sealed interface LevelReference {
  val encoded: String

  /** Local IDs stay byte-for-byte compatible with existing routes and training-session rows. */
  data class Local(val id: String) : LevelReference {
    init {
      require(id.isNotEmpty()) { "A local level ID cannot be empty." }
    }

    override val encoded: String = id
  }

  data class Remote(
    val courseId: Long,
    val mode: TrainingFlow,
    val levelId: String,
  ) : LevelReference {
    init {
      require(courseId > 0) { "A course ID must be positive." }
      require(levelId.isNotEmpty()) { "A course level ID cannot be empty." }
    }

    override val encoded: String =
      "$RemotePrefix$courseId:${mode.apiName}:${levelId.encodeHex()}"
  }

  companion object {
    private const val RemotePrefix = "course:"

    fun decode(value: String): LevelReference {
      require(value.isNotEmpty()) { "A level reference cannot be empty." }
      if (!value.startsWith(RemotePrefix)) return Local(value)

      val parts = value.split(':', limit = 4)
      require(parts.size == 4 && parts[0] == "course") { "Malformed remote level reference." }
      val courseId = parts[1].toLongOrNull()
      require(courseId != null && courseId > 0) { "Malformed course ID in level reference." }
      val mode = TrainingFlow.entries.firstOrNull { it.apiName == parts[2] }
      requireNotNull(mode) { "Unsupported course mode in level reference." }
      val levelId = parts[3].decodeHex()
      require(levelId.isNotEmpty()) { "Malformed course level ID in level reference." }
      return Remote(courseId, mode, levelId)
    }
  }
}

val TrainingFlow.apiName: String
  get() = name.lowercase()

private val HexDigits = "0123456789abcdef"

private fun String.encodeHex(): String {
  val bytes = encodeToByteArray()
  return buildString(bytes.size * 2) {
    for (byte in bytes) {
      val value = byte.toInt() and 0xff
      append(HexDigits[value ushr 4])
      append(HexDigits[value and 0x0f])
    }
  }
}

private fun String.decodeHex(): String {
  require(length % 2 == 0 && all { it.digitToIntOrNull(16) != null }) {
    "Malformed course level ID encoding."
  }
  val bytes = ByteArray(length / 2) { index ->
    val high = this[index * 2].digitToInt(16)
    val low = this[index * 2 + 1].digitToInt(16)
    ((high shl 4) or low).toByte()
  }
  return bytes.decodeToString(throwOnInvalidSequence = true)
}
