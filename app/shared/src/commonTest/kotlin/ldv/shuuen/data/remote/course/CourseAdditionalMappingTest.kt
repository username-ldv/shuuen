package ldv.shuuen.data.remote.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.decodeFromJsonElement
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.generator.ChordFigure
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.melodies.domain.MidiFileSource

class CourseAdditionalMappingTest {
  @Test
  fun mapsRelativeSinglesAndEveryContextVariant() {
    val item = map(TrainingFlow.Singles, singlesJson)
    val level = assertIs<PlayableTrainingLevel.Singles>(item.playable).level
    val config = assertIs<LevelConfig.Singles.Relative>(level.levelConfig)

    assertEquals(7, level.questionsNumber)
    assertEquals(12, config.rotateEveryQuestions)
    assertEquals(25, config.tuneInconsistencyCents)
    assertEquals(Degree.D1, config.scaleConfig.degreeStates.single().degree)
    assertEquals(2, level.context?.nodes?.size)
  }

  @Test
  fun mapsAbsoluteChordsAndWeightedFigures() {
    val item = map(TrainingFlow.Chords, chordsJson)
    val level = assertIs<PlayableTrainingLevel.Chords>(item.playable).level
    val config = assertIs<LevelConfig.Chords.Absolute>(level.levelConfig)

    assertEquals(ChordAnswerOrder.FromBottom, level.answerOrder)
    assertEquals(3, level.chordSize.min)
    assertEquals(4, level.chordSize.max)
    assertTrue(level.sustainNotes)
    assertIs<ChordFigure.Stacked>(config.chordStyle.figures.first().figure)
    assertIs<ChordFigure.FreePick>(config.chordStyle.figures.last().figure)
  }

  @Test
  fun mapsBackendMidiOnlyAfterResourceIdsMatch() {
    val item = map(TrainingFlow.Melodies, midiJson)
    val level = assertIs<PlayableTrainingLevel.Melodies>(item.playable).level
    val config = assertIs<LevelConfig.Melodies.Midi>(level.config)
    val source = assertIs<MidiFileSource.Backend>(config.midiSource)

    assertEquals(12, source.melodyId)
    assertEquals(34, source.variantId)
    assertEquals("/api/v1/library/variants/34/download", source.downloadUrl)
  }

  private fun map(mode: TrainingFlow, payload: String) =
    CourseDefinitionMapper(ApiJson).map(
      9,
      mode,
      ApiJson.decodeFromJsonElement<CourseLevelDto>(ApiJson.parseToJsonElement(payload)),
    )
}

private val singlesJson =
  """
  {
    "id":"single-relative","progression_group_id":"group","name":"Relative","source":"user",
    "definition":{
      "level_config":{
        "type":"relative",
        "scale_config":{"scale_type":"Chromatic","degree_states":[{"degree":"D1","active":true}]},
        "rotate_every_questions":12,"tune_inconsistency_cents":25
      },
      "context":{
        "id":"context","source":"local","nodes":[
          {
            "first_degree":{"degree":"D1","octave":2},"extra_degrees":["D3"],
            "sustain":{"type":"finite","duration_ms":1500},
            "duration":{"type":"finite","duration_in_questions":3},
            "setup_melody":null,"relative_direction":"Down"
          },
          {
            "first_degree":{"degree":"D5","octave":3},"extra_degrees":[],
            "sustain":{"type":"endless"},"duration":{"type":"immediate"},
            "setup_melody":null,"relative_direction":"Up"
          }
        ]
      },
      "questions_number":7,"range":{"from":{"midi_index":21},"to":{"midi_index":108}}
    },
    "sort_order":1,"is_public":true,"sections":[]
  }
  """.trimIndent()

private val chordsJson =
  """
  {
    "id":"chords-absolute","progression_group_id":"group","name":"Chords","source":"imported",
    "definition":{
      "level_config":{
        "type":"absolute",
        "scales":[{"root":"C","scale_type":"Major","pitch_states":[{"pitch":"C","active":true}]}],
        "rotate_every_questions":null,
        "chord_style":{
          "id":"triads","name":"Triads","description":"Diatonic shapes","tier":"Intermediate",
          "figures":[
            {"figure":{"type":"stacked","ladder_steps":[0,2,4]},"weight":2.0},
            {"figure":{"type":"free_pick"},"weight":1.0}
          ]
        }
      },
      "context":null,"questions_number":10,
      "range":{"from":{"midi_index":36},"to":{"midi_index":84}},
      "chord_size":{"min":3,"max":4},"sustain_notes":true,"answer_order":"FromBottom"
    },
    "sort_order":2,"is_public":true,"sections":[]
  }
  """.trimIndent()

private val midiJson =
  """
  {
    "id":"midi-level","progression_group_id":"group","name":"MIDI","source":"imported",
    "definition":{
      "config":{
        "type":"midi",
        "file":{"type":"backend","melody_id":12,"variant_id":34,"file_name":"piece.mid"},
        "use_original_velocities":true
      },
      "context":null
    },
    "sort_order":3,"is_public":true,
    "midi":{"melody_id":12,"variant_id":34,"download_url":"/api/v1/library/variants/34/download"},
    "sections":[]
  }
  """.trimIndent()
