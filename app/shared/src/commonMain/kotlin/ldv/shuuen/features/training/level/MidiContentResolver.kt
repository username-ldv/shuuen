package ldv.shuuen.features.training.level

interface MidiContentResolver {
  suspend fun resolve(source: MidiFileSource): ByteArray
}
