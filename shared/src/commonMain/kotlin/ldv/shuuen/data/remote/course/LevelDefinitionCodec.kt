package ldv.shuuen.data.remote.course

import io.github.vinceglb.filekit.PlatformFileSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.SetupMelody
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.generator.ChordFigure
import ldv.shuuen.core.music.generator.ChordStyle
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MidiFileSource
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.data.remote.ApiJsonQualifier
import org.koin.core.annotation.Named

/**
 * Encodes local levels with the same stable, snake-case definition schema used by course levels.
 * This avoids persisting Kotlin class names on the backend and leaves definitions directly useful
 * to a future web/statistics client.
 */
internal class LevelDefinitionCodec(
  @Named(ApiJsonQualifier) private val json: Json,
) {
  private val courseMapper = CourseDefinitionMapper(json)

  fun encode(level: SinglesLevel): JsonElement =
    json.encodeToJsonElement(
      SinglesDefinitionDto(
        levelConfig = singlesConfig(level.levelConfig),
        context = level.context?.toDto(),
        questionsNumber = level.questionsNumber,
        range = level.range.toDto(),
      )
    )

  fun encode(level: MelodiesLevel): JsonElement =
    json.encodeToJsonElement(
      MelodiesDefinitionDto(
        config = melodiesConfig(level.config),
        context = level.context?.toDto(),
      )
    )

  fun encode(level: ChordsLevel): JsonElement =
    json.encodeToJsonElement(
      ChordsDefinitionDto(
        levelConfig = chordsConfig(level.levelConfig),
        context = level.context?.toDto(),
        questionsNumber = level.questionsNumber,
        range = level.range.toDto(),
        chordSize = ChordSizeRangeDto(level.chordSize.min, level.chordSize.max),
        sustainNotes = level.sustainNotes,
        answerOrder = level.answerOrder.name,
      )
    )

  /** Decodes through the course validator/mapper, then restores the local level id. */
  fun decode(
    flow: TrainingFlow,
    id: String,
    name: String,
    source: LevelSource,
    definition: JsonElement,
  ): PlayableTrainingLevel {
    val midi =
      if (flow == TrainingFlow.Melodies) {
        val configElement = json.decodeFromJsonElement<MelodiesDefinitionDto>(definition).config
        val type = json.decodeFromJsonElement<TypeDto>(configElement).type
        if (type == "midi") {
          val file = json.decodeFromJsonElement<MidiMelodyConfigDto>(configElement).file
          if (file.type == "backend" && file.melodyId != null && file.variantId != null) {
            CourseMidiResourceDto(
              melodyId = file.melodyId,
              variantId = file.variantId,
              downloadUrl =
                file.downloadUrl ?: "/api/v1/library/variants/${file.variantId}/download",
            )
          } else {
            null
          }
        } else {
          null
        }
      } else {
        null
      }
    val mapped =
      courseMapper.map(
        courseId = 1,
        mode = flow,
        dto =
          CourseLevelDto(
            // Course-level ids are capped more tightly than local ids; this is
            // only a validation carrier and the real local id is restored below.
            id = "sync",
            progressionGroupId = "sync",
            name = name,
            source = source.dbValue,
            definition = definition,
            sortOrder = 0,
            isPublic = false,
            midi = midi,
            sections = emptyList(),
          ),
      ).playable
    return when (mapped) {
      is PlayableTrainingLevel.Singles ->
        PlayableTrainingLevel.Singles(mapped.level.copy(id = id))
      is PlayableTrainingLevel.Melodies ->
        PlayableTrainingLevel.Melodies(mapped.level.copy(id = id))
      is PlayableTrainingLevel.Chords ->
        PlayableTrainingLevel.Chords(mapped.level.copy(id = id))
    }
  }

  private fun singlesConfig(config: LevelConfig.Singles): JsonElement =
    when (config) {
      is LevelConfig.Singles.Absolute ->
        json.encodeToJsonElement(
          SinglesAbsoluteConfigDto(
            type = "absolute",
            scales = config.scales.map { it.toDto(includeType = false) },
            rotateEveryQuestions = config.rotateEveryQuestions,
            tuneInconsistencyCents = config.tuneInconsistencyCents,
          )
        )
      is LevelConfig.Singles.Relative ->
        json.encodeToJsonElement(
          SinglesRelativeConfigDto(
            type = "relative",
            scaleConfig = config.scaleConfig.toDto(includeType = false),
            rotateEveryQuestions = config.rotateEveryQuestions,
            tuneInconsistencyCents = config.tuneInconsistencyCents,
          )
        )
    }

  private fun chordsConfig(config: LevelConfig.Chords): JsonElement =
    when (config) {
      is LevelConfig.Chords.Absolute ->
        json.encodeToJsonElement(
          ChordsAbsoluteConfigDto(
            type = "absolute",
            scales = config.scales.map { it.toDto(includeType = false) },
            rotateEveryQuestions = config.rotateEveryQuestions,
            chordStyle = config.chordStyle.toDto(),
          )
        )
      is LevelConfig.Chords.Relative ->
        json.encodeToJsonElement(
          ChordsRelativeConfigDto(
            type = "relative",
            scaleConfig = config.scaleConfig.toDto(includeType = false),
            rotateEveryQuestions = config.rotateEveryQuestions,
            chordStyle = config.chordStyle.toDto(),
          )
        )
    }

  private fun melodiesConfig(config: LevelConfig.Melodies): JsonElement =
    when (config) {
      is LevelConfig.Melodies.Random ->
        json.encodeToJsonElement(
          RandomMelodyConfigDto(
            type = "random",
            scaleConfig = config.scaleConfig.toDtoElement(),
            questionsNumber = config.questionsNumber,
            notesPerSequence = config.notesPerSequence,
            tempo = config.tempo,
            range = config.range.toDto(),
            rotateEveryQuestions = config.rotateEveryQuestions,
            melodyStyle = config.melodyStyle.toDto(),
            tuneInconsistencyCents = config.tuneInconsistencyCents,
          )
        )
      is LevelConfig.Melodies.Midi -> {
        val file =
          when (val source = config.midiSource) {
            is MidiFileSource.Local ->
              MidiFileReferenceDto(
                type = "local",
                path = platformFilePath(source.platformFile),
                fileName = config.fileName,
              )
            is MidiFileSource.Backend ->
              MidiFileReferenceDto(
                type = "backend",
                melodyId = source.melodyId,
                variantId = source.variantId,
                fileName = source.fileName,
                downloadUrl = source.downloadUrl,
              )
          }
        json.encodeToJsonElement(
          MidiMelodyConfigDto(
            type = "midi",
            file = file,
            useOriginalVelocities = config.useOriginalVelocities,
            backingFilePath = config.backingFile?.let(::platformFilePath),
            backingFileName = config.backingFileName,
            backingOffsetMs = config.backingOffsetMs,
          )
        )
      }
    }

  private fun ScaleConfig.toDtoElement(): JsonElement =
    when (this) {
      is ScaleConfig.AbsoluteScaleConfig -> json.encodeToJsonElement(toDto(includeType = true))
      is ScaleConfig.RelativeScaleConfig -> json.encodeToJsonElement(toDto(includeType = true))
    }

  private fun ScaleConfig.AbsoluteScaleConfig.toDto(includeType: Boolean) =
    AbsoluteScaleConfigDto(
      type = "absolute".takeIf { includeType },
      root = root.name,
      scaleType = scaleType.name,
      // Older chromatic levels contain the tonic twice (root and octave).
      // Collapse them to the unique pitch-class representation used by the API.
      pitchStates =
        pitchStates.distinctBy { it.pitch }.map { state ->
          PitchStateDto(
            pitch = state.pitch.name,
            active = pitchStates.any { it.pitch == state.pitch && it.active },
          )
        },
    )

  private fun ScaleConfig.RelativeScaleConfig.toDto(includeType: Boolean) =
    RelativeScaleConfigDto(
      type = "relative".takeIf { includeType },
      scaleType = scaleType.name,
      degreeStates =
        degreeStates.distinctBy { it.degree }.map { state ->
          DegreeStateDto(
            degree = state.degree.name,
            active = degreeStates.any { it.degree == state.degree && it.active },
          )
        },
    )

  private fun Note.toDto() = NoteDto(midiIndex)

  private fun NoteRange.toDto() = NoteRangeDto(from.toDto(), to.toDto())

  private fun DegreeContext.toDto() =
    DegreeContextDto(
      id = id,
      source = source.dbValue,
      nodes = nodes.map { it.toDto() },
      name = name,
    )

  private fun DegreeContextNode.toDto() =
    DegreeContextNodeDto(
      firstDegree = DegreeWithOctaveDto(firstDegree.degree.name, firstDegree.octave),
      extraDegrees = extraDegrees.map { it.name },
      sustain =
        when (val value = sustain) {
          Sustain.Endless -> SustainDto("endless")
          is Sustain.Finite -> SustainDto("finite", value.duration.inWholeMilliseconds)
        },
      duration =
        when (val value = duration) {
          is ContextDuration.Finite -> ContextDurationDto("finite", value.durationInQuestions)
          ContextDuration.Immediate -> ContextDurationDto("immediate")
          ContextDuration.Endless -> ContextDurationDto("endless")
          ContextDuration.SameAsScaleRotation -> ContextDurationDto("same_as_scale_rotation")
        },
      setupMelody = setupMelody?.toDto(),
      relativeDirection = relativeDirection.name,
    )

  private fun SetupMelody.toDto() = SetupMelodyDto(melody.toDto(), repeat.name)

  private fun RelativeMelody.toDto() =
    RelativeMelodyDto(
      firstDegree = DegreeWithOctaveDto(firstDegree.degree.name, firstDegree.octave),
      extraDegrees = extraDegrees.map { DirectedDegreeDto(it.degree.name, it.direction.name) },
    )

  private fun ChordStyle.toDto() =
    ChordStyleDto(
      id = id,
      name = name,
      description = description,
      tier = tier.name,
      figures =
        figures.map { weighted ->
          val figure =
            when (val value = weighted.figure) {
              ChordFigure.FreePick -> ChordFigureDto("free_pick")
              is ChordFigure.Stacked -> ChordFigureDto("stacked", value.ladderSteps)
            }
          WeightedChordFigureDto(figure, weighted.weight)
        },
    )

  private fun MelodyStyle.toDto() =
    MelodyStyleDto(
      id = id,
      name = name,
      description = description,
      tier = tier.name,
      figures =
        figures.map { weighted ->
          WeightedRhythmFigureDto(
            figure =
              RhythmFigureDto(
                values = weighted.figure.values.map { it.name },
                contour = weighted.figure.contour,
                ladder = weighted.figure.ladder.name,
              ),
            weight = weighted.weight,
          )
        },
      noteWeights =
        NoteWeightsDto(
          intervalWeights = noteWeights.intervalWeights,
          degreeWeights = noteWeights.degreeWeights.mapKeys { it.key.name },
          chordToneBoost = noteWeights.chordToneBoost,
        ),
    )

  private fun platformFilePath(file: io.github.vinceglb.filekit.PlatformFile): String =
    json.encodeToJsonElement(PlatformFileSerializer, file).jsonPrimitive.content
}
