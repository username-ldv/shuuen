package ldv.shuuen.features.training.melodies.domain

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.PlatformFileSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface MidiFileSource {
  data class Local(val platformFile: PlatformFile) : MidiFileSource

  data class Backend(
    val melodyId: Long,
    val variantId: Long,
    val fileName: String,
    val downloadUrl: String,
  ) : MidiFileSource
}

/**
 * Keeps legacy Room JSON compatible: local files are still encoded with FileKit's original
 * serializer, while backend references use an explicit object shape.
 */
object MidiFileSourceSerializer : KSerializer<MidiFileSource> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MidiFileSource")

  override fun serialize(encoder: Encoder, value: MidiFileSource) {
    when (value) {
      is MidiFileSource.Local ->
        encoder.encodeSerializableValue(PlatformFileSerializer, value.platformFile)

      is MidiFileSource.Backend -> {
        val jsonEncoder = encoder as? JsonEncoder
          ?: throw SerializationException("Backend MIDI references require JSON encoding.")
        jsonEncoder.encodeJsonElement(
          buildJsonObject {
            put("type", "backend")
            put("melody_id", value.melodyId)
            put("variant_id", value.variantId)
            put("file_name", value.fileName)
            put("download_url", value.downloadUrl)
          }
        )
      }
    }
  }

  override fun deserialize(decoder: Decoder): MidiFileSource {
    val jsonDecoder = decoder as? JsonDecoder
      ?: return MidiFileSource.Local(decoder.decodeSerializableValue(PlatformFileSerializer))
    val element = jsonDecoder.decodeJsonElement()
    if (element !is JsonObject) {
      return MidiFileSource.Local(
        jsonDecoder.json.decodeFromJsonElement(PlatformFileSerializer, element)
      )
    }
    val type = element["type"]?.jsonPrimitive?.contentOrNull
    if (type != "backend") {
      return MidiFileSource.Local(
        jsonDecoder.json.decodeFromJsonElement(PlatformFileSerializer, element)
      )
    }
    fun requiredString(name: String): String =
      element[name]?.jsonPrimitive?.contentOrNull
        ?: throw SerializationException("Backend MIDI reference is missing $name.")
    fun requiredLong(name: String): Long =
      requiredString(name).toLongOrNull()
        ?: throw SerializationException("Backend MIDI reference has invalid $name.")
    return MidiFileSource.Backend(
      melodyId = requiredLong("melody_id"),
      variantId = requiredLong("variant_id"),
      fileName = requiredString("file_name"),
      downloadUrl = requiredString("download_url"),
    )
  }
}
