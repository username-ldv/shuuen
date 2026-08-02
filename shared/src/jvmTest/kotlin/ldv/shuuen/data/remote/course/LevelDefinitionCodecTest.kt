package ldv.shuuen.data.remote.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.defaultContext
import ldv.shuuen.core.music.generator.ChordStyles
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel

class LevelDefinitionCodecTest {
  private val codec = LevelDefinitionCodec(ApiJson)
  private val scale =
    ScaleConfig.AbsoluteScaleConfig(
      root = Pitch.C,
      scaleType = ScaleType.Major,
      pitchStates =
        Pitch.entries.map { ScaleConfig.ScaleItemState.ScalePitchState(it, it == Pitch.C) },
    )
  private val range = NoteRange(Note(Pitch.C, 3), Note(Pitch.C, 5))

  @Test
  fun allLevelKindsRoundTripThroughStableDefinitions() {
    val singles =
      SinglesLevel(
        id = "singles-id",
        name = "Singles",
        levelConfig =
          LevelConfig.Singles.Absolute(
            scales = listOf(scale),
            rotateEveryQuestions = 5,
            tuneInconsistencyCents = 12,
          ),
        context = defaultContext,
        source = LevelSource.User,
        questionsNumber = 20,
        range = range,
      )
    val melodies =
      MelodiesLevel(
        id = "melodies-id",
        name = "Melodies",
        config =
          LevelConfig.Melodies.Random(
            scaleConfig = scale,
            questionsNumber = 10,
            notesPerSequence = 4,
            tempo = 96,
            range = range,
            melodyStyle = MelodyStyles.Default,
          ),
        context = defaultContext,
        source = LevelSource.User,
      )
    val chords =
      ChordsLevel(
        id = "chords-id",
        name = "Chords",
        levelConfig =
          LevelConfig.Chords.Absolute(
            scales = listOf(scale),
            rotateEveryQuestions = 5,
            chordStyle = ChordStyles.Default,
          ),
        context = defaultContext,
        source = LevelSource.User,
        questionsNumber = 15,
        range = range,
        chordSize = ChordSizeRange(3, 5),
        sustainNotes = true,
        answerOrder = ChordAnswerOrder.FromBottom,
      )

    val decodedSingles =
      assertIs<PlayableTrainingLevel.Singles>(
        codec.decode(
          TrainingFlow.Singles,
          singles.id,
          singles.name,
          singles.source,
          codec.encode(singles),
        )
      ).level
    val decodedMelodies =
      assertIs<PlayableTrainingLevel.Melodies>(
        codec.decode(
          TrainingFlow.Melodies,
          melodies.id,
          melodies.name,
          melodies.source,
          codec.encode(melodies),
        )
      ).level
    val decodedChords =
      assertIs<PlayableTrainingLevel.Chords>(
        codec.decode(
          TrainingFlow.Chords,
          chords.id,
          chords.name,
          chords.source,
          codec.encode(chords),
        )
      ).level

    assertEquals(singles, decodedSingles)
    assertEquals(melodies, decodedMelodies)
    assertEquals(chords, decodedChords)
  }

  @Test
  fun legacyChromaticOctaveDuplicatesAreNormalizedForSync() {
    val legacyScale =
      ScaleConfig.AbsoluteScaleConfig(
        root = Pitch.C,
        scaleType = ScaleType.Chromatic,
        pitchStates =
          (Pitch.entries + Pitch.C).map {
            ScaleConfig.ScaleItemState.ScalePitchState(it, active = true)
          },
      )
    val level =
      MelodiesLevel(
        id = "legacy-chromatic",
        name = "Legacy chromatic",
        config =
          LevelConfig.Melodies.Random(
            scaleConfig = legacyScale,
            questionsNumber = 10,
            notesPerSequence = 4,
            tempo = 96,
            range = range,
          ),
        context = null,
        source = LevelSource.BuiltIn,
      )

    val definition = codec.encode(level)
    val pitchStates =
      definition.jsonObject.getValue("config").jsonObject
        .getValue("scale_config").jsonObject
        .getValue("pitch_states").jsonArray
    val decoded =
      assertIs<PlayableTrainingLevel.Melodies>(
        codec.decode(
          TrainingFlow.Melodies,
          level.id,
          level.name,
          level.source,
          definition,
        )
      ).level.config as LevelConfig.Melodies.Random
    val decodedScale = decoded.scaleConfig as ScaleConfig.AbsoluteScaleConfig

    assertEquals(12, pitchStates.size)
    assertEquals(12, decodedScale.pitchStates.size)
  }
}
