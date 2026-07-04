package ldv.shuuen.data.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.generator.ChordStyles
import ldv.shuuen.features.training.common.asConfigDegreeStates
import ldv.shuuen.features.training.common.asPitchStates
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig

/**
 * The chords level config — including the chord style — is stored as one JSON column by the
 * Room type converter; these tests pin that round-trip. (The style deliberately lives inside
 * the config: the entity's other columns are mapped field by field, where a new field would be
 * silently dropped.)
 */
class ChordsLevelJsonTest {
  private fun relativeConfig(): LevelConfig.Chords =
    LevelConfig.Chords.Relative(
      scaleConfig =
        ScaleConfig.RelativeScaleConfig(
          scaleType = ScaleType.Major,
          degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
        ),
    )

  @Test
  fun relativeConfigRoundTripsANonDefaultChordStyle() {
    // Typed as the interface: the Room converter encodes polymorphically, so the test must too.
    val config: LevelConfig.Chords =
      LevelConfig.Chords.Relative(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.Major,
            degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
          ),
        chordStyle = ChordStyles.LeaningDiatonic,
      )

    val decoded: LevelConfig.Chords = RoomJson.decode(RoomJson.encode(config))

    assertEquals(config, decoded)
  }

  @Test
  fun absoluteConfigRoundTripsANonDefaultChordStyle() {
    val config: LevelConfig.Chords =
      LevelConfig.Chords.Absolute(
        scales =
          listOf(
            ScaleConfig.AbsoluteScaleConfig(
              root = Pitch.C,
              scaleType = ScaleType.Major,
              pitchStates = Scale.major(Pitch.C).asPitchStates(),
            )
          ),
        chordStyle = ChordStyles.RootTriads,
      )

    val decoded: LevelConfig.Chords = RoomJson.decode(RoomJson.encode(config))

    assertEquals(config, decoded)
  }

  @Test
  fun configSavedBeforeChordStyleFieldStillDecodes() {
    // A level saved by the previous app version has no chordStyle key in its config JSON.
    val legacyJson =
      JsonObject(
        Json.parseToJsonElement(RoomJson.encode(relativeConfig())).jsonObject.filterKeys {
          it != "chordStyle"
        }
      ).toString()

    val decoded: LevelConfig.Chords = RoomJson.decode(legacyJson)

    assertTrue(decoded is LevelConfig.Chords.Relative)
    assertEquals(ChordStyles.AnythingGoes, decoded.chordStyle)
  }
}
