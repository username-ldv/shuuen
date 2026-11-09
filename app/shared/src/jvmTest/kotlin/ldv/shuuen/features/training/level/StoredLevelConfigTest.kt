package ldv.shuuen.features.training.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import ldv.shuuen.core.database.RoomJson
import ldv.shuuen.core.music.ScaleType

/**
 * Level configs already saved on devices name their classes by the package they were first written
 * from (`features.training.domain`). These rows are copied from a real database; if a class moves
 * or is renamed without keeping its serial name, they stop loading.
 */
class StoredLevelConfigTest {
  @Test
  fun aSavedSinglesConfigStillLoads() {
    val stored =
      """{"type":"ldv.shuuen.features.training.domain.LevelConfig.Singles.Relative","scaleConfig":{"scaleType":"Major","degreeStates":[{"degree":"D1","active":true},{"degree":"D2","active":true},{"degree":"D3","active":true},{"degree":"D4","active":true},{"degree":"D5","active":true},{"degree":"D6","active":true},{"degree":"D7","active":true}]},"rotateEveryQuestions":5}"""

    val config = SinglesLevelTypeConverter().stringToLevelConfig(stored)

    val relative = assertIs<LevelConfig.Singles.Relative>(config)
    assertEquals(5, relative.rotateEveryQuestions)
    assertEquals(
      "ldv.shuuen.features.training.domain.LevelConfig.Singles.Relative",
      RoomJson.json.parseToJsonElement(RoomJson.encode<LevelConfig.Singles>(config)).typeName(),
    )
  }

  @Test
  fun aSavedMelodiesConfigStillLoads() {
    val stored =
      """{"type":"ldv.shuuen.features.training.domain.LevelConfig.Melodies.Random","scaleConfig":{"type":"ldv.shuuen.features.training.domain.ScaleConfig.AbsoluteScaleConfig","root":"C","scaleType":"Major","pitchStates":[{"pitch":"C","active":true},{"pitch":"D","active":true},{"pitch":"E","active":true},{"pitch":"F","active":true},{"pitch":"G","active":true},{"pitch":"A","active":true},{"pitch":"B","active":true}]},"questionsNumber":20,"notesPerSequence":8,"tempo":96,"range":{"from":{"midiIndex":36},"to":{"midiIndex":96}},"rotateEveryQuestions":null}"""

    val config = MelodiesLevelTypeConverter().stringToMelodiesConfig(stored)

    val random = assertIs<LevelConfig.Melodies.Random>(config)
    val scale = assertIs<ScaleConfig.AbsoluteScaleConfig>(random.scaleConfig)
    assertEquals(ScaleType.Major, scale.scaleType)
    assertEquals(20, random.questionsNumber)
  }

  @Test
  fun everyStoredClassKeepsItsOriginalName() {
    val chords: LevelConfig.Chords =
      LevelConfig.Chords.Relative(
        ScaleConfig.RelativeScaleConfig(ScaleType.Major, degreeStates = emptyList())
      )

    assertEquals(
      "ldv.shuuen.features.training.domain.LevelConfig.Chords.Relative",
      RoomJson.json.parseToJsonElement(RoomJson.encode(chords)).typeName(),
    )
  }

  private fun kotlinx.serialization.json.JsonElement.typeName(): String =
    (this as kotlinx.serialization.json.JsonObject)["type"].toString().trim('"')
}
