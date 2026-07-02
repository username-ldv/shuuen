package ldv.shuuen.core.music.generator

import kotlin.random.Random
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch

interface ChordNotesGenerator {
  /** The next chord's notes, lowest first. */
  fun next(): List<Note>
}

/**
 * Uniformly random chords from [allowedNotes]: each question picks a size in [minSize]..[maxSize]
 * and that many distinct notes. A pitch class may repeat across octaves; [maxRepeatsForSize]
 * limits how many extra same-class copies a chord of a given size may contain (null = unlimited).
 *
 * Only feasible sizes are drawn — a size must fit both the available note count and its repeat
 * budget. When the whole [minSize]..[maxSize] span is infeasible (e.g. a tiny scale over a narrow
 * range), the generator falls back to the largest size it can actually build.
 */
class NaiveRandomChordGenerator(
  private val allowedNotes: List<Note>,
  private val minSize: Int,
  private val maxSize: Int,
  private val maxRepeatsForSize: ((Int) -> Int)? = null,
  private val random: Random = Random.Default,
) : ChordNotesGenerator {
  private val distinctPitchClasses = allowedNotes.distinctBy { it.pitch }.size

  init {
    require(allowedNotes.isNotEmpty()) { "allowedNotes can't be empty" }
    require(minSize in 1..maxSize) { "Chord size range must satisfy 1 <= min <= max, was $minSize..$maxSize." }
  }

  private fun repeatBudget(size: Int): Int = maxRepeatsForSize?.invoke(size) ?: Int.MAX_VALUE

  private fun isFeasible(size: Int): Boolean =
    size <= allowedNotes.size && (size - distinctPitchClasses).coerceAtLeast(0) <= repeatBudget(size)

  override fun next(): List<Note> {
    val feasibleSizes = (minSize..maxSize).filter(::isFeasible)
    val size =
      if (feasibleSizes.isNotEmpty()) feasibleSizes.random(random)
      else (1..maxSize).last(::isFeasible)

    // Greedy pick over a shuffled order: a note of an already-used class costs one unit of the
    // repeat budget and is skipped once it's spent. Feasibility guarantees the chord fills up —
    // every still-unused class keeps all of its notes in the remaining stream.
    val budget = repeatBudget(size)
    val taken = ArrayList<Note>(size)
    val classCounts = mutableMapOf<Pitch, Int>()
    var repeatsUsed = 0
    for (note in allowedNotes.shuffled(random)) {
      if (taken.size == size) break
      val count = classCounts[note.pitch] ?: 0
      if (count > 0 && repeatsUsed >= budget) continue
      if (count > 0) repeatsUsed++
      classCounts[note.pitch] = count + 1
      taken += note
    }
    return taken.sortedBy { it.midiIndex }
  }
}
