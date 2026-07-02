package ldv.shuuen.data.database

import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.Scale
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.features.training.common.asConfigDegreeStates
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig

/**
 * The melodies level config is stored as JSON by the Room type converter; these tests pin the
 * round-trip, including the [PlatformFile] reference that serializes as a plain path string.
 */
class MelodiesLevelConfigJsonTest {
  @Test
  fun randomConfigRoundTrips() {
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.Major,
            degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
          ),
        questionsNumber = 20,
        notesPerSequence = 4,
        tempo = 96,
        range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5)),
        rotateEveryQuestions = 10,
      )

    val decoded: LevelConfig.Melodies = RoomJson.decode(RoomJson.encode(config))

    assertEquals(config, decoded)
  }

  @Test
  fun endlessRandomConfigKeepsNullSequenceAndQuestions() {
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.NaturalMinor,
            degreeStates = Scale.naturalMinor(Pitch.A).asConfigDegreeStates(),
          ),
        questionsNumber = null,
        notesPerSequence = null,
        tempo = 120,
        range = NoteRange(Note(Pitch.C, 2), Note(Pitch.C, 7)),
        rotateEveryQuestions = null,
      )

    val decoded: LevelConfig.Melodies = RoomJson.decode(RoomJson.encode(config))

    assertEquals(config, decoded)
  }

  @Test
  fun randomConfigSavedBeforeRotationFieldStillDecodes() {
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.Major,
            degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
          ),
        questionsNumber = 20,
        notesPerSequence = 4,
        tempo = 96,
        range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5)),
        rotateEveryQuestions = 10,
      )
    // A level saved by the previous app version has no rotateEveryQuestions key.
    val legacyJson =
      JsonObject(
        Json.parseToJsonElement(RoomJson.encode(config)).jsonObject.filterKeys {
          it != "rotateEveryQuestions"
        }
      ).toString()

    val decoded: LevelConfig.Melodies = RoomJson.decode(legacyJson)

    assertTrue(decoded is LevelConfig.Melodies.Random)
    assertEquals(null, decoded.rotateEveryQuestions)
    assertEquals(4, decoded.notesPerSequence)
  }

  @Test
  fun randomConfigRoundTripsANonDefaultMelodyStyle() {
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.Major,
            degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
          ),
        questionsNumber = 20,
        notesPerSequence = 4,
        tempo = 96,
        range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5)),
        melodyStyle = MelodyStyles.WalkingLines,
      )

    val decoded: LevelConfig.Melodies = RoomJson.decode(RoomJson.encode(config))

    assertEquals(config, decoded)
  }

  @Test
  fun randomConfigSavedBeforeMelodyStyleFieldStillDecodes() {
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.RelativeScaleConfig(
            scaleType = ScaleType.Major,
            degreeStates = Scale.major(Pitch.C).asConfigDegreeStates(),
          ),
        questionsNumber = 20,
        notesPerSequence = 4,
        tempo = 96,
        range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5)),
      )
    // A level saved by the previous app version has no melodyStyle key.
    val legacyJson =
      JsonObject(
        Json.parseToJsonElement(RoomJson.encode(config)).jsonObject.filterKeys {
          it != "melodyStyle"
        }
      ).toString()

    val decoded: LevelConfig.Melodies = RoomJson.decode(legacyJson)

    assertTrue(decoded is LevelConfig.Melodies.Random)
    assertEquals(MelodyStyles.SteadyQuarters, decoded.melodyStyle)
  }

  @Test
  fun midiConfigRoundTripsTheFileReference() {
    val original = File("D:/melodies/tune.mid")
    val config: LevelConfig.Melodies =
      LevelConfig.Melodies.Midi(
        file = PlatformFile(original),
        fileName = "tune.mid",
        useOriginalVelocities = true,
      )

    val decoded = RoomJson.decode<LevelConfig.Melodies>(RoomJson.encode(config))

    assertTrue(decoded is LevelConfig.Melodies.Midi)
    assertEquals("tune.mid", decoded.fileName)
    assertTrue(decoded.useOriginalVelocities)
    assertEquals(original.absolutePath, decoded.file.file.absolutePath)
  }
}
