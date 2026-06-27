package ldv.shuuen.core.music

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.random.Random

@Serializable
data class Note(val midiIndex: Int) {
  init {
    require(midiIndex in MidiMin..MidiMax) {
      "MIDI index must be in the 88-key piano range $MidiMin..$MidiMax, was $midiIndex."
    }
  }

  constructor(pitch: Pitch, octave: Int = 4) : this(midiIndexFor(pitch, octave))

  val pitch: Pitch
    get() = Pitch.entries[midiIndex.floorMod(12)]

  val octave: Int
    get() = when (pianoKeyNumber) {
      in 1..3 -> 0
      in 4..88 -> ceil((pianoKeyNumber.toDouble() - 3.0) / 12.0).toInt()
      else -> error("Piano key is outside the supported range: $pianoKeyNumber.")
    }

  val name: String
    get() = "$pitch$octave"

  val fullName: String
    get() = if (!pitch.toString().contains("#")) name else "${pitch}/${(pitch + 1).name}♭$octave"

  val pianoKeyNumber: Int
    get() = midiIndex - MidiOffset + 1

  fun degree(tonic: Pitch): Degree = Degree.fromOffset(midiIndex - Note(tonic).midiIndex)

  // todo: add safePlus, safeMinus with clamping the values?
  operator fun plus(semitones: Int): Note = Note(midiIndex + semitones)

  operator fun minus(semitones: Int): Note = Note(midiIndex - semitones)

  operator fun rangeTo(to: Note): List<Note> =
    (midiIndex..to.midiIndex).map(::Note)

  fun rangeUntil(until: Note): List<Note> =
    (midiIndex until until.midiIndex).map(::Note)

  fun next(pitch: Pitch): Note {
    val semitonesUp =
      (pitch.ordinal - this.pitch.ordinal).mod(12).let { distance ->
        if (distance == 0) 12 else distance
      }
    return this + semitonesUp
  }

  fun previous(pitch: Pitch): Note {
    val semitonesDown =
      (this.pitch.ordinal - pitch.ordinal).mod(12).let { distance ->
        if (distance == 0) 12 else distance
      }
    return this - semitonesDown
  }

  override fun toString(): String = name

  companion object {
    const val MidiOffset: Int = 21
    const val MidiMin: Int = 21
    const val MidiMax: Int = 108

    fun random(
      noteRange: NoteRange = NoteRange(Note(Pitch.A, 0), Note(Pitch.C, 8)),
      random: Random = Random.Default,
    ): Note {
      require(noteRange.from.midiIndex <= noteRange.to.midiIndex) { "From note must be lower than or equal to to note." }
      return Note(random.nextInt(noteRange.from.midiIndex, noteRange.to.midiIndex + 1))
    }

    private fun midiIndexFor(pitch: Pitch, octave: Int): Int {
      require(octave in 0..8) { "Octave must be in 0..8, was $octave." }
      val midiIndex = if (octave == 0) {
        MidiOffset + pitch.ordinal - Pitch.A.ordinal
      } else {
        3 + MidiOffset + ((octave - 1) * 12) + pitch.ordinal
      }
      require(midiIndex in MidiMin..MidiMax) {
        "Pitch $pitch$octave is outside the 88-key piano range."
      }
      return midiIndex
    }
  }
}
