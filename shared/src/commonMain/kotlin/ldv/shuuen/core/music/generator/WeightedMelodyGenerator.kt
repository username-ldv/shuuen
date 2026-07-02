package ldv.shuuen.core.music.generator

import kotlin.math.abs
import kotlin.random.Random
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.core.music.Pitch

/** A generated note plus the rhythm value it should be played with. */
data class TimedNote(val note: Note, val value: NoteValue)

/**
 * Generates melody figures shaped by a [MelodyStyle]: each call picks one of the style's rhythm
 * figures by weight, then fills its notes — free positions through the weighted note picker
 * (melodic interval x degree weights against the previous note), contour positions by walking
 * the allowed notes stepwise. The generator remembers the last note it produced, so consecutive
 * figures connect into one line.
 */
class WeightedMelodyGenerator(
  private val style: MelodyStyle,
  private val root: Pitch,
  range: NoteRange,
  allowedPitches: List<Pitch>,
  private val random: Random = Random.Default,
) {
  /** Every allowed note in the range, low to high; contours step along this ladder. */
  private val ladder: List<Note> =
    (range.from..range.to).filter { note -> allowedPitches.any { it == note.pitch } }

  private val figureWeightSum = style.figures.sumOf { it.weight }

  private var previous: Note? = null

  /** The next figure's notes. Throws [NoSuchElementException] when no notes are allowed at all. */
  fun nextFigure(): List<TimedNote> {
    val figure = pickFigure()
    return figure.values.mapIndexed { i, value ->
      val note =
        when (val step = if (i == 0) null else figure.contour.getOrNull(i - 1)) {
          null -> pickWeighted()
          else -> stepAlongLadder(previous ?: pickWeighted(), step)
        }
      previous = note
      TimedNote(note, value)
    }
  }

  private fun pickFigure(): RhythmFigure {
    var roll = random.nextDouble() * figureWeightSum
    for (weighted in style.figures) {
      roll -= weighted.weight
      if (roll < 0) return weighted.figure
    }
    return style.figures.last().figure
  }

  private fun pickWeighted(): Note {
    if (ladder.isEmpty()) throw NoSuchElementException("No allowed notes to generate from.")
    val prev = previous ?: return pickBy { style.noteWeights.degreeWeight(it.degree(root)) }
    return pickBy { candidate ->
      style.noteWeights.intervalWeight(abs(candidate.midiIndex - prev.midiIndex)) *
        style.noteWeights.degreeWeight(candidate.degree(root))
    }
  }

  private fun pickBy(weightOf: (Note) -> Double): Note {
    val weights = ladder.map(weightOf)
    val total = weights.sum()
    if (total <= 0) return ladder.random(random)
    var roll = random.nextDouble() * total
    for (i in ladder.indices) {
      roll -= weights[i]
      if (roll < 0) return ladder[i]
    }
    return ladder.last()
  }

  /** Moves [step] ladder positions from [from], reflecting off the range edges to stay inside. */
  private fun stepAlongLadder(from: Note, step: Int): Note {
    val index = ladder.indexOf(from).coerceAtLeast(0)
    val target = index + step
    val reflected =
      when {
        target < 0 -> -target
        target > ladder.lastIndex -> 2 * ladder.lastIndex - target
        else -> target
      }
    return ladder[reflected.coerceIn(0, ladder.lastIndex)]
  }
}
