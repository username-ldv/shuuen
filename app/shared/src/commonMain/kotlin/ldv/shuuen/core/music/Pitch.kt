package ldv.shuuen.core.music

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Pitch {
  C, CSharp, D, DSharp, E, F, FSharp, G, GSharp, A, ASharp, B;

  override fun toString(): String = name.replace("Sharp", "#")

  fun toFlatString(): String =
    if (!name.contains("Sharp")) name else "${entries[(ordinal + 1) % entries.size].name}♭"

  operator fun plus(semitones: Int): Pitch = entries[(ordinal + semitones).floorMod(entries.size)]

  operator fun minus(semitones: Int): Pitch = this + -semitones

  fun asRoot(other: Pitch): Degree {
    return Degree.fromOffset((other.ordinal - this.ordinal + 12).mod(12))
  }

  companion object {
    fun random(random: Random = Random.Default): Pitch = entries[random.nextInt(entries.size)]

    fun fromOrdinal(ordinal: Int): Pitch = entries[ordinal.floorMod(entries.size)]

    fun fromName(name: String): Pitch? =
      entries.firstOrNull { it.toString().equals(name, ignoreCase = true) }
        ?: entries.firstOrNull { it.toFlatString().equals(name, ignoreCase = true) }
        ?: entries.firstOrNull {
          it.toFlatString().replace("♭", "b").equals(name, ignoreCase = true)
        }
  }
}

internal fun Int.floorMod(modulus: Int): Int {
  val value = this % modulus
  return if (value < 0) value + modulus else value
}
