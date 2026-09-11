package ldv.shuuen.data.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.Sustain

class ContextJsonTest {
  @Test
  fun contextNodeRoundTripsRelativeDirection() {
    val node = contextNode(relativeDirection = DegreeDirection.Down)

    val decoded: List<DegreeContextNode> = RoomJson.decode(RoomJson.encode(listOf(node)))

    assertEquals(DegreeDirection.Down, decoded.single().relativeDirection)
  }

  @Test
  fun contextNodeSavedBeforeRelativeDirectionDecodesAsAbove() {
    val node = contextNode(relativeDirection = DegreeDirection.Down)
    val encoded = RoomJson.encode(listOf(node))
    val legacyJson =
      JsonArray(
        Json.parseToJsonElement(encoded).jsonArray.map { element ->
          JsonObject(element.jsonObject.filterKeys { it != "relativeDirection" })
        }
      ).toString()

    val decoded: List<DegreeContextNode> = RoomJson.decode(legacyJson)

    assertEquals(DegreeDirection.Up, decoded.single().relativeDirection)
  }

  private fun contextNode(relativeDirection: DegreeDirection): DegreeContextNode =
    DegreeContextNode(
      firstDegree = DegreeWithOctave(Degree.D1, 3),
      extraDegrees = listOf(Degree.D3, Degree.D5),
      sustain = Sustain.Endless,
      duration = ContextDuration.Endless,
      setupMelody = null,
      relativeDirection = relativeDirection,
    )
}
