package ldv.shuuen.data.database

import kotlinx.serialization.json.Json

object RoomJson {
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
  }

  inline fun <reified T> encode(value: T): String =
    json.encodeToString(value)

  inline fun <reified T> decode(value: String): T =
    json.decodeFromString(value)
}