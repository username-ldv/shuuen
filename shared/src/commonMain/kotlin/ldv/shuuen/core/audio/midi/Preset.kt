package ldv.shuuen.core.audio.midi

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
  val bank: Int,
  val id: Int,
  val name: String? = null,
) {
  fun toPacked(): Int = (id and 0xffff) or ((bank and 0xffff) shl 16)

  companion object {
    fun fromPacked(packed: Int, name: String? = null): Preset =
      Preset(
        id = packed and 0xffff,
        bank = (packed shr 16) and 0xffff,
        name = name,
      )
  }
}