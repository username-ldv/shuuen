package ldv.shuuen.data.remote.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.decodeFromJsonElement
import ldv.shuuen.data.remote.ApiJson
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseMappingException
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.ScaleConfig

class CourseDefinitionMapperTest {
  @Test
  fun mapsTheActualCTonic60BpmDefinition() {
    val dto = ApiJson.decodeFromJsonElement<CourseLevelDto>(ApiJson.parseToJsonElement(cTonicLevelJson))

    val item = CourseDefinitionMapper(ApiJson).map(7, TrainingFlow.Melodies, dto)
    val level = assertIs<PlayableTrainingLevel.Melodies>(item.playable).level
    val config = assertIs<LevelConfig.Melodies.Random>(level.config)
    val scale = assertIs<ScaleConfig.AbsoluteScaleConfig>(config.scaleConfig)

    assertEquals("course:7:melodies:736565642d632d746f6e69632d303630", level.id)
    assertEquals(20, config.questionsNumber)
    assertEquals(8, config.notesPerSequence)
    assertEquals(60, config.tempo)
    assertEquals(36, config.range.from.midiIndex)
    assertEquals(96, config.range.to.midiIndex)
    assertEquals(7, scale.pitchStates.count { it.active })
    assertEquals("Steady quarters", config.melodyStyle.name)
    assertEquals(listOf(NoteValue.Quarter), config.melodyStyle.figures.single().figure.values)
    assertEquals(null, item.navigation?.previousLevelId)
    assertEquals("seed-c-tonic-075", item.navigation?.nextLevelId)
    assertEquals(0L, item.navigation?.position)
    assertEquals(11L, item.navigation?.total)

    val context = requireNotNull(level.context)
    val node = context.nodes.single()
    assertEquals(Degree.D1, node.firstDegree.degree)
    assertEquals(2, node.firstDegree.octave)
    assertIs<ContextDuration.SameAsScaleRotation>(node.duration)
    val setup = requireNotNull(node.setupMelody)
    assertEquals(Degree.D1, setup.melody.firstDegree.degree)
    assertEquals(3, setup.melody.firstDegree.octave)
    assertEquals(listOf(Degree.D3, Degree.D5, Degree.D1), setup.melody.extraDegrees.map { it.degree })
  }

  @Test
  fun mappingErrorsIdentifyTheLevelAndOffendingField() {
    val malformed = cTonicLevelJson.replace("\"tempo\": 60", "\"tempo\": 0")
    val dto = ApiJson.decodeFromJsonElement<CourseLevelDto>(ApiJson.parseToJsonElement(malformed))

    val error = assertFailsWith<CourseMappingException> {
      CourseDefinitionMapper(ApiJson).map(7, TrainingFlow.Melodies, dto)
    }

    assertEquals("seed-c-tonic-060", error.levelId)
    assertEquals("definition.config.tempo", error.field)
    assertTrue(error.message.orEmpty().contains("between 20 and 400"))
  }
}

private val cTonicLevelJson =
  """
  {
    "id": "seed-c-tonic-060",
    "progression_group_id": "c-major",
    "name": "C major — 60 BPM",
    "source": "built_in",
    "definition": {
      "config": {
        "type": "random",
        "scale_config": {
          "type": "absolute",
          "root": "C",
          "scale_type": "Major",
          "pitch_states": [
            {"pitch":"C","active":true}, {"pitch":"CSharp","active":false},
            {"pitch":"D","active":true}, {"pitch":"DSharp","active":false},
            {"pitch":"E","active":true}, {"pitch":"F","active":true},
            {"pitch":"FSharp","active":false}, {"pitch":"G","active":true},
            {"pitch":"GSharp","active":false}, {"pitch":"A","active":true},
            {"pitch":"ASharp","active":false}, {"pitch":"B","active":true}
          ]
        },
        "questions_number": 20,
        "notes_per_sequence": 8,
        "tempo": 60,
        "range": {"from":{"midi_index":36},"to":{"midi_index":96}},
        "rotate_every_questions": null,
        "melody_style": {
          "id": "steady-quarters",
          "name": "Steady quarters",
          "description": "Every note a plain quarter, picked fully at random — the original drill.",
          "tier": "Beginner",
          "figures": [
            {"figure":{"values":["Quarter"],"contour":[],"ladder":"Scale"},"weight":1.0}
          ],
          "note_weights": {"interval_weights":[],"degree_weights":{},"chord_tone_boost":1.0}
        },
        "tune_inconsistency_cents": 0
      },
      "context": {
        "id": "seed-c-tonic-drone",
        "source": "built_in",
        "name": "C tonic drone",
        "nodes": [{
          "first_degree":{"degree":"D1","octave":2},
          "extra_degrees":[],
          "sustain":{"type":"endless"},
          "duration":{"type":"same_as_scale_rotation"},
          "setup_melody":{
            "melody":{
              "first_degree":{"degree":"D1","octave":3},
              "extra_degrees":[
                {"degree":"D3","direction":"Up"},
                {"degree":"D5","direction":"Up"},
                {"degree":"D1","direction":"Up"}
              ]
            },
            "repeat":"Once"
          },
          "relative_direction":"Up"
        }]
      }
    },
    "sort_order": 0,
    "is_public": true,
    "sections": [],
    "navigation": {
      "previous_level_id": null,
      "next_level_id": "seed-c-tonic-075",
      "position": 0,
      "total": 11
    }
  }
  """.trimIndent()
