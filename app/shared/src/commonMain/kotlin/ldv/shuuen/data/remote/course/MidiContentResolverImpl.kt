package ldv.shuuen.data.remote.course

import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ldv.shuuen.features.training.melodies.domain.MidiContentResolver
import ldv.shuuen.features.training.melodies.domain.MidiFileSource

internal class MidiContentResolverImpl(
  private val api: CourseApi,
) : MidiContentResolver {
  private val cache = mutableMapOf<Long, ByteArray>()
  private val cacheMutex = Mutex()

  override suspend fun resolve(source: MidiFileSource): ByteArray =
    when (source) {
      is MidiFileSource.Local ->
        runCatching { source.platformFile.readBytes() }
          .getOrElse { throw IllegalStateException("Couldn't read the local MIDI file. Has it moved?", it) }

      is MidiFileSource.Backend -> {
        cacheMutex.withLock { cache[source.variantId] }?.let { return it }
        val bytes =
          runCatching { api.downloadBytes(source.downloadUrl) }
            .getOrElse {
              throw IllegalStateException(
                "Couldn't download ${source.fileName} (variant ${source.variantId}).",
                it,
              )
            }
        require(bytes.isNotEmpty()) { "Downloaded MIDI variant ${source.variantId} was empty." }
        cacheMutex.withLock { cache[source.variantId] = bytes }
        bytes
      }
    }
}
