package ldv.shuuen.core.music.generator

import kotlin.random.Random
import ldv.shuuen.core.music.Note

/**
 * Generates chords shaped by a [ChordStyle]: each question picks one of the style's figures by
 * weight — a [ChordFigure.Stacked] shape built on a random bass position of the allowed-note
 * ladder, or a [ChordFigure.FreePick] delegated to the uniform [NaiveRandomChordGenerator].
 *
 * Only figures the level can actually produce take part in the draw: a stacked shape must fit
 * the chord-size range and have room on the ladder. When no figure is feasible (e.g. a triads
 * preset on a two-note chord-size level), generation falls back to the free pick, mirroring the
 * naive generator's own size fallback.
 */
class WeightedChordGenerator(
  allowedNotes: List<Note>,
  private val style: ChordStyle,
  private val minSize: Int,
  private val maxSize: Int,
  maxRepeatsForSize: ((Int) -> Int)? = null,
  private val random: Random = Random.Default,
) : ChordNotesGenerator {
  /** Every allowed note, low to high; stacked shapes climb this ladder. */
  private val ladder: List<Note> = allowedNotes.sortedBy { it.midiIndex }

  private val freePick =
    NaiveRandomChordGenerator(allowedNotes, minSize, maxSize, maxRepeatsForSize, random)

  private val feasibleFigures: List<WeightedChordFigure> =
    style.figures.filter { isFeasible(it.figure) }

  private val feasibleWeightSum = feasibleFigures.sumOf { it.weight }

  override fun next(): List<Note> =
    when (val figure = pickFigure()) {
      null, is ChordFigure.FreePick -> freePick.next()
      is ChordFigure.Stacked -> build(figure.ladderSteps)
    }

  private fun pickFigure(): ChordFigure? {
    if (feasibleFigures.isEmpty()) return null
    var roll = random.nextDouble() * feasibleWeightSum
    for (weighted in feasibleFigures) {
      roll -= weighted.weight
      if (roll < 0) return weighted.figure
    }
    return feasibleFigures.last().figure
  }

  private fun isFeasible(figure: ChordFigure): Boolean =
    when (figure) {
      is ChordFigure.FreePick -> true
      is ChordFigure.Stacked ->
        figure.ladderSteps.size in minSize..maxSize &&
          figure.ladderSteps.last() <= ladder.lastIndex
    }

  private fun build(ladderSteps: List<Int>): List<Note> {
    val bass = random.nextInt(ladder.size - ladderSteps.last())
    return ladderSteps.map { ladder[bass + it] }
  }
}
