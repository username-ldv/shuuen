package ldv.shuuen.features.training.level.midi

interface MidiContentResolver {
  suspend fun resolve(source: MidiFileSource): ByteArray
}
