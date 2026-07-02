package ldv.shuuen.core.music.generator

import kotlin.random.Random
import ldv.shuuen.core.music.Note

interface ChordNotesGenerator {
  /** The next chord's notes, lowest first. */
  fun next(): List<Note>
}

/**
 * Uniformly random chords from [allowedNotes]: each question picks a size in [minSize]..[maxSize]
 * and that many notes with distinct pitch classes. Distinct pitch classes are required because the
 * answer inputs work per pitch class — a doubled octave would be indistinguishable from a single
 * note. The size is capped at the number of distinct pitch classes [allowedNotes] offers.
 */
class NaiveRandomChordGenerator(
  private val allowedNotes: List<Note>,
  minSize: Int,
  maxSize: Int,
  private val random: Random = Random.Default,
) : ChordNotesGenerator {
  private val availablePitchClasses = allowedNotes.distinctBy { it.pitch }.size
  private val minSize = minSize.coerceAtMost(availablePitchClasses).coerceAtLeast(1)
  private val maxSize = maxSize.coerceIn(this.minSize, availablePitchClasses)

  init {
    require(allowedNotes.isNotEmpty()) { "allowedNotes can't be empty" }
  }

  override fun next(): List<Note> {
    val size = random.nextInt(minSize, maxSize + 1)
    return allowedNotes
      .shuffled(random)
      .distinctBy { it.pitch }
      .take(size)
      .sortedBy { it.midiIndex }
  }
}
