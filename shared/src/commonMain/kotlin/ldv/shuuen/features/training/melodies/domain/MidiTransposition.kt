package ldv.shuuen.features.training.melodies.domain

import kotlin.random.Random
import kotlinx.serialization.Serializable

const val MinimumMidiTransposition = -6
const val MaximumMidiTransposition = 6

@Serializable
enum class MidiTranspositionMode {
  Random,
  Defined,
}

/** A per-run customization for imported MIDI levels. */
@Serializable
data class MidiTransposition(
  val mode: MidiTranspositionMode = MidiTranspositionMode.Defined,
  val semitones: Int = 0,
) {
  init {
    require(semitones in MinimumMidiTransposition..MaximumMidiTransposition) {
      "MIDI transposition must be between $MinimumMidiTransposition and $MaximumMidiTransposition."
    }
  }

  /** Resolves Random once when a level starts; Defined always returns its selected amount. */
  fun resolve(random: Random = Random.Default): Int =
    when (mode) {
      MidiTranspositionMode.Random ->
        random.nextInt(MinimumMidiTransposition, MaximumMidiTransposition + 1)
      MidiTranspositionMode.Defined -> semitones
    }
}
