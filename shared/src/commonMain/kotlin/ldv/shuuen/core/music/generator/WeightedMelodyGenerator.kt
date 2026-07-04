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
 *
 * A context-aware style additionally reacts to the chord set via [setActiveChord]: chord tones
 * get the style's [NoteWeights.chordToneBoost] in the picker, and [FigureLadder.Chord] contours
 * walk only the chord's tones (arpeggios).
 */
class WeightedMelodyGenerator(
  private val style: MelodyStyle,
  private val root: Pitch,
  range: NoteRange,
  allowedPitches: List<Pitch>,
  private val random: Random = Random.Default,
) {
  /** Every allowed note in the range, low to high; scale contours step along this ladder. */
  private val ladder: List<Note> =
    (range.from..range.to).filter { note -> allowedPitches.any { it == note.pitch } }

  private val figureWeightSum = style.figures.sumOf { it.weight }

  private var previous: Note? = null

  /** Pitch classes of the sounding context chord, when it can actually steer the melody. */
  private var chordPitches: Set<Pitch>? = null

  /** The ladder's notes restricted to [chordPitches]; empty when no chord is active. */
  private var chordLadder: List<Note> = emptyList()

  /**
   * Sets (or clears, with null) the context chord the melody should lean toward. A chord of
   * fewer than two distinct pitch classes — a bare drone — is ignored: the base degree weights
   * already handle tonic gravity.
   */
  fun setActiveChord(pitches: Set<Pitch>?) {
    val effective = pitches?.takeIf { it.size >= 2 }
    chordPitches = effective
    chordLadder =
      if (effective == null) emptyList() else ladder.filter { it.pitch in effective }
  }

  /** The next figure's notes. Throws [NoSuchElementException] when no notes are allowed at all. */
  fun nextFigure(): List<TimedNote> {
    val figure = pickFigure()
    val contourLadder =
      when {
        figure.ladder == FigureLadder.Chord && chordLadder.size >= 2 -> chordLadder
        else -> ladder
      }
    return figure.values.mapIndexed { i, value ->
      val note =
        when (val step = if (i == 0) null else figure.contour.getOrNull(i - 1)) {
          null -> pickWeighted()
          else -> stepAlong(contourLadder, previous ?: pickWeighted(), step)
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
    val prev = previous ?: return pickBy { baseWeight(it) }
    return pickBy { candidate ->
      style.noteWeights.intervalWeight(abs(candidate.midiIndex - prev.midiIndex)) *
        baseWeight(candidate)
    }
  }

  private fun baseWeight(candidate: Note): Double {
    val chordFactor =
      if (chordPitches?.contains(candidate.pitch) == true) style.noteWeights.chordToneBoost else 1.0
    return style.noteWeights.degreeWeight(candidate.degree(root)) * chordFactor
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

  /**
   * Moves [step] positions along [walkLadder] from [from], reflecting off the edges to stay
   * inside. [from] may not be a member (e.g. a passing tone before an arpeggio figure); the
   * walk then starts from the nearest position at or below it.
   */
  private fun stepAlong(walkLadder: List<Note>, from: Note, step: Int): Note {
    val exact = walkLadder.indexOfLast { it.midiIndex <= from.midiIndex }
    val index = exact.coerceAtLeast(0)
    val target = index + step
    val reflected =
      when {
        target < 0 -> -target
        target > walkLadder.lastIndex -> 2 * walkLadder.lastIndex - target
        else -> target
      }
    return walkLadder[reflected.coerceIn(0, walkLadder.lastIndex)]
  }
}
