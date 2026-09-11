package ldv.shuuen.features.training.melodies.domain

interface MidiContentResolver {
  suspend fun resolve(source: MidiFileSource): ByteArray
}
