package ldv.shuuen.data.remote.course

import io.github.vinceglb.filekit.PlatformFile
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeDirection
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.DirectedDegree
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.RelativeMelody
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.SetupMelody
import ldv.shuuen.core.music.SetupMelodyRepeat
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.core.music.generator.ChordFigure
import ldv.shuuen.core.music.generator.ChordStyle
import ldv.shuuen.core.music.generator.ChordStyles
import ldv.shuuen.core.music.generator.FigureLadder
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.MelodyStyles
import ldv.shuuen.core.music.generator.NoteWeights
import ldv.shuuen.core.music.generator.RhythmFigure
import ldv.shuuen.core.music.generator.StyleTier
import ldv.shuuen.core.music.generator.WeightedChordFigure
import ldv.shuuen.core.music.generator.WeightedFigure
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.chords.domain.ChordSizeRange
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseLevelNavigation
import ldv.shuuen.features.training.course.domain.CourseMappingException
import ldv.shuuen.features.training.course.domain.CourseSection
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MidiFileSource
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.data.remote.ApiJsonQualifier
import org.koin.core.annotation.Named

internal class CourseDefinitionMapper(
  @Named(ApiJsonQualifier) private val json: Json,
) {
  fun map(courseId: Long, mode: TrainingFlow, dto: CourseLevelDto): CourseLevelItem {
    val context = MappingContext(dto.id)
    return context.at("definition") {
      context.require(dto.id.isNotBlank() && dto.id.length <= 64, "id", "is required and must be at most 64 characters")
      context.require(dto.name.isNotBlank() && dto.name.length <= 220, "name", "is required and must be at most 220 characters")
      val reference = LevelReference.Remote(courseId, mode, dto.id)
      val source = context.levelSource(dto.source, "source")
      val playable =
        when (mode) {
          TrainingFlow.Singles ->
            PlayableTrainingLevel.Singles(mapSingles(dto, reference, source, context))
          TrainingFlow.Melodies ->
            PlayableTrainingLevel.Melodies(mapMelodies(dto, reference, source, context))
          TrainingFlow.Chords ->
            PlayableTrainingLevel.Chords(mapChords(dto, reference, source, context))
        }
      CourseLevelItem(
        reference = reference,
        playable = playable,
        progressionGroupId = dto.progressionGroupId.nonBlank(context, "progression_group_id"),
        sortOrder = dto.sortOrder,
        sections =
          dto.sections.mapIndexed { index, section ->
            context.require(section.libraryGroupId > 0, "sections[$index].library_group_id", "must be positive")
            context.require(section.depth > 0, "sections[$index].depth", "must be positive")
            CourseSection(
              libraryGroupId = section.libraryGroupId,
              name = section.name.nonBlank(context, "sections[$index].name"),
              path = section.path.nonBlank(context, "sections[$index].path"),
              depth = section.depth,
            )
          },
        sourceCourseId = courseId,
        mode = mode,
        navigation =
          dto.navigation?.let { navigation ->
            context.require(navigation.position >= 0, "navigation.position", "must not be negative")
            context.require(navigation.total > 0, "navigation.total", "must be positive")
            context.require(
              navigation.position < navigation.total,
              "navigation.position",
              "must be less than navigation.total",
            )
            CourseLevelNavigation(
              previousLevelId = navigation.previousLevelId,
              nextLevelId = navigation.nextLevelId,
              position = navigation.position,
              total = navigation.total,
            )
          },
      )
    }
  }

  private fun mapSingles(
    dto: CourseLevelDto,
    reference: LevelReference.Remote,
    source: LevelSource,
    context: MappingContext,
  ): SinglesLevel {
    val definition = context.decode<SinglesDefinitionDto>(dto.definition, "definition")
    context.positiveOptional(definition.questionsNumber, "definition.questions_number")
    val configType = context.decode<TypeDto>(definition.levelConfig, "definition.level_config").type
    val config: LevelConfig.Singles =
      when (configType) {
        "absolute" -> {
          val value = context.decode<SinglesAbsoluteConfigDto>(definition.levelConfig, "definition.level_config")
          context.require(value.scales.isNotEmpty(), "definition.level_config.scales", "must not be empty")
          context.rotationAndTuning(value.rotateEveryQuestions, value.tuneInconsistencyCents, "definition.level_config")
          LevelConfig.Singles.Absolute(
            scales = value.scales.mapIndexed { index, scale -> context.absoluteScale(scale, "definition.level_config.scales[$index]") },
            rotateEveryQuestions = value.rotateEveryQuestions,
            tuneInconsistencyCents = value.tuneInconsistencyCents,
          )
        }
        "relative" -> {
          val value = context.decode<SinglesRelativeConfigDto>(definition.levelConfig, "definition.level_config")
          context.rotationAndTuning(value.rotateEveryQuestions, value.tuneInconsistencyCents, "definition.level_config")
          LevelConfig.Singles.Relative(
            scaleConfig = context.relativeScale(value.scaleConfig, "definition.level_config.scale_config"),
            rotateEveryQuestions = value.rotateEveryQuestions,
            tuneInconsistencyCents = value.tuneInconsistencyCents,
          )
        }
        else -> context.fail("definition.level_config.type", "expected absolute or relative, was '$configType'")
      }
    return SinglesLevel(
      id = reference.encoded,
      name = dto.name,
      levelConfig = config,
      context = definition.context?.let { context.degreeContext(it, "definition.context") },
      source = source,
      questionsNumber = definition.questionsNumber,
      range = context.noteRange(definition.range, "definition.range"),
    )
  }

  private fun mapMelodies(
    dto: CourseLevelDto,
    reference: LevelReference.Remote,
    source: LevelSource,
    context: MappingContext,
  ): MelodiesLevel {
    val definition = context.decode<MelodiesDefinitionDto>(dto.definition, "definition")
    val configType = context.decode<TypeDto>(definition.config, "definition.config").type
    val config: LevelConfig.Melodies =
      when (configType) {
        "random" -> {
          val value = context.decode<RandomMelodyConfigDto>(definition.config, "definition.config")
          context.positiveOptional(value.questionsNumber, "definition.config.questions_number")
          context.positiveOptional(value.notesPerSequence, "definition.config.notes_per_sequence")
          context.require(
            value.notesPerSequence != null || value.questionsNumber == null,
            "definition.config.questions_number",
            "must be null for an endless melody stream",
          )
          context.require(value.tempo in 20..400, "definition.config.tempo", "must be between 20 and 400")
          context.rotationAndTuning(value.rotateEveryQuestions, value.tuneInconsistencyCents, "definition.config")
          LevelConfig.Melodies.Random(
            scaleConfig = context.scale(value.scaleConfig, "definition.config.scale_config"),
            questionsNumber = value.questionsNumber,
            notesPerSequence = value.notesPerSequence,
            tempo = value.tempo,
            range = context.noteRange(value.range, "definition.config.range"),
            rotateEveryQuestions = value.rotateEveryQuestions,
            melodyStyle = value.melodyStyle?.let { context.melodyStyle(it, "definition.config.melody_style") } ?: MelodyStyles.Default,
            tuneInconsistencyCents = value.tuneInconsistencyCents,
          )
        }
        "midi" -> {
          val value = context.decode<MidiMelodyConfigDto>(definition.config, "definition.config")
          val fileName = value.file.fileName.nonBlank(context, "definition.config.file.file_name")
          val midiSource =
            when (value.file.type) {
              "backend" -> {
                val melodyId = value.file.melodyId
                  ?: context.fail("definition.config.file.melody_id", "is required")
                val variantId = value.file.variantId
                  ?: context.fail("definition.config.file.variant_id", "is required")
                context.require(melodyId > 0, "definition.config.file.melody_id", "must be positive")
                context.require(variantId > 0, "definition.config.file.variant_id", "must be positive")
                context.require(value.file.path.isNullOrBlank(), "definition.config.file.path", "is not valid for a backend file")
                val resource = dto.midi ?: context.fail("midi", "response resource is required for a backend MIDI level")
                context.require(resource.melodyId == melodyId, "midi.melody_id", "does not match the definition")
                context.require(resource.variantId == variantId, "midi.variant_id", "does not match the definition")
                MidiFileSource.Backend(
                  melodyId = melodyId,
                  variantId = variantId,
                  fileName = fileName,
                  downloadUrl = resource.downloadUrl.nonBlank(context, "midi.download_url"),
                )
              }
              "local" -> {
                val path = value.file.path?.nonBlank(context, "definition.config.file.path")
                  ?: context.fail("definition.config.file.path", "is required")
                context.require(value.file.melodyId == null, "definition.config.file.melody_id", "is not valid for a local file")
                context.require(value.file.variantId == null, "definition.config.file.variant_id", "is not valid for a local file")
                MidiFileSource.Local(PlatformFile(path))
              }
              else -> context.fail("definition.config.file.type", "expected backend or local, was '${value.file.type}'")
            }
          LevelConfig.Melodies.Midi(
            midiSource = midiSource,
            fileName = fileName,
            useOriginalVelocities = value.useOriginalVelocities,
            backingFile = value.backingFilePath?.let(::PlatformFile),
            backingFileName = value.backingFileName,
            backingOffsetMs = value.backingOffsetMs,
          )
        }
        else -> context.fail("definition.config.type", "expected random or midi, was '$configType'")
      }
    return MelodiesLevel(
      id = reference.encoded,
      name = dto.name,
      config = config,
      context = definition.context?.let { context.degreeContext(it, "definition.context") },
      source = source,
    )
  }

  private fun mapChords(
    dto: CourseLevelDto,
    reference: LevelReference.Remote,
    source: LevelSource,
    context: MappingContext,
  ): ChordsLevel {
    val definition = context.decode<ChordsDefinitionDto>(dto.definition, "definition")
    context.positiveOptional(definition.questionsNumber, "definition.questions_number")
    context.require(
      definition.chordSize.min in ChordSizeRange.MinSize..definition.chordSize.max &&
        definition.chordSize.max <= ChordSizeRange.MaxSize,
      "definition.chord_size",
      "must satisfy 2 <= min <= max <= 10",
    )
    val configType = context.decode<TypeDto>(definition.levelConfig, "definition.level_config").type
    val config: LevelConfig.Chords =
      when (configType) {
        "absolute" -> {
          val value = context.decode<ChordsAbsoluteConfigDto>(definition.levelConfig, "definition.level_config")
          context.require(value.scales.isNotEmpty(), "definition.level_config.scales", "must not be empty")
          context.positiveOptional(value.rotateEveryQuestions, "definition.level_config.rotate_every_questions")
          LevelConfig.Chords.Absolute(
            scales = value.scales.mapIndexed { index, scale -> context.absoluteScale(scale, "definition.level_config.scales[$index]") },
            rotateEveryQuestions = value.rotateEveryQuestions,
            chordStyle = value.chordStyle?.let { context.chordStyle(it, "definition.level_config.chord_style") } ?: ChordStyles.Default,
          )
        }
        "relative" -> {
          val value = context.decode<ChordsRelativeConfigDto>(definition.levelConfig, "definition.level_config")
          context.positiveOptional(value.rotateEveryQuestions, "definition.level_config.rotate_every_questions")
          LevelConfig.Chords.Relative(
            scaleConfig = context.relativeScale(value.scaleConfig, "definition.level_config.scale_config"),
            rotateEveryQuestions = value.rotateEveryQuestions,
            chordStyle = value.chordStyle?.let { context.chordStyle(it, "definition.level_config.chord_style") } ?: ChordStyles.Default,
          )
        }
        else -> context.fail("definition.level_config.type", "expected absolute or relative, was '$configType'")
      }
    val answerOrder = ChordAnswerOrder.entries.firstOrNull { it.name == definition.answerOrder }
      ?: context.fail("definition.answer_order", "unknown value '${definition.answerOrder}'")
    return ChordsLevel(
      id = reference.encoded,
      name = dto.name,
      levelConfig = config,
      context = definition.context?.let { context.degreeContext(it, "definition.context") },
      source = source,
      questionsNumber = definition.questionsNumber,
      range = context.noteRange(definition.range, "definition.range"),
      chordSize = ChordSizeRange(definition.chordSize.min, definition.chordSize.max),
      sustainNotes = definition.sustainNotes,
      answerOrder = answerOrder,
    )
  }

  private fun String.nonBlank(context: MappingContext, field: String): String {
    context.require(isNotBlank(), field, "must not be blank")
    return this
  }

  private inner class MappingContext(private val levelId: String) {
    fun fail(field: String, detail: String, cause: Throwable? = null): Nothing =
      throw CourseMappingException(levelId, field, detail, cause)

    inline fun <T> at(field: String, block: () -> T): T =
      try {
        block()
      } catch (error: CourseMappingException) {
        throw error
      } catch (error: Throwable) {
        fail(field, error.message ?: error::class.simpleName.orEmpty(), error)
      }

    inline fun <reified T> decode(element: JsonElement, field: String): T =
      try {
        json.decodeFromJsonElement<T>(element)
      } catch (error: SerializationException) {
        fail(field, error.message ?: "could not be decoded", error)
      }

    fun require(value: Boolean, field: String, detail: String) {
      if (!value) fail(field, detail)
    }

    fun positiveOptional(value: Int?, field: String) {
      require(value == null || value > 0, field, "must be null or positive")
    }

    fun rotationAndTuning(rotation: Int?, tuning: Int, field: String) {
      positiveOptional(rotation, "$field.rotate_every_questions")
      require(tuning in 0..100, "$field.tune_inconsistency_cents", "must be between 0 and 100")
    }

    fun levelSource(value: String, field: String): LevelSource =
      when (value) {
        "built_in" -> LevelSource.BuiltIn
        "user" -> LevelSource.User
        "imported" -> LevelSource.Imported
        else -> fail(field, "unknown level source '$value'")
      }

    fun pitch(value: String, field: String): Pitch =
      Pitch.entries.firstOrNull { it.name == value } ?: fail(field, "unknown pitch '$value'")

    fun degree(value: String, field: String): Degree =
      Degree.entries.firstOrNull { it.name == value } ?: fail(field, "unknown degree '$value'")

    fun scaleType(value: String, field: String): ScaleType =
      ScaleType.entries.firstOrNull { it.name == value } ?: fail(field, "unknown scale type '$value'")

    fun note(value: NoteDto, field: String): Note =
      at(field) { Note(value.midiIndex) }

    fun noteRange(value: NoteRangeDto, field: String): NoteRange {
      val from = note(value.from, "$field.from.midi_index")
      val to = note(value.to, "$field.to.midi_index")
      require(from.midiIndex <= to.midiIndex, field, "from must not be higher than to")
      return NoteRange(from, to)
    }

    fun absoluteScale(value: AbsoluteScaleConfigDto, field: String): ScaleConfig.AbsoluteScaleConfig {
      require(value.type == null || value.type == "absolute", "$field.type", "expected absolute")
      require(value.pitchStates.isNotEmpty(), "$field.pitch_states", "must not be empty")
      val states = value.pitchStates.mapIndexed { index, state ->
        ScaleConfig.ScaleItemState.ScalePitchState(
          pitch(state.pitch, "$field.pitch_states[$index].pitch"),
          state.active,
        )
      }
      require(states.map { it.pitch }.distinct().size == states.size, "$field.pitch_states", "contains duplicate pitches")
      require(states.any { it.active }, field, "must contain at least one active pitch")
      return ScaleConfig.AbsoluteScaleConfig(
        root = pitch(value.root, "$field.root"),
        scaleType = scaleType(value.scaleType, "$field.scale_type"),
        pitchStates = states,
      )
    }

    fun relativeScale(value: RelativeScaleConfigDto, field: String): ScaleConfig.RelativeScaleConfig {
      require(value.type == null || value.type == "relative", "$field.type", "expected relative")
      require(value.degreeStates.isNotEmpty(), "$field.degree_states", "must not be empty")
      val states = value.degreeStates.mapIndexed { index, state ->
        ScaleConfig.ScaleItemState.ScaleDegreeState(
          degree(state.degree, "$field.degree_states[$index].degree"),
          state.active,
        )
      }
      require(states.map { it.degree }.distinct().size == states.size, "$field.degree_states", "contains duplicate degrees")
      require(states.any { it.active }, field, "must contain at least one active degree")
      return ScaleConfig.RelativeScaleConfig(
        scaleType = scaleType(value.scaleType, "$field.scale_type"),
        degreeStates = states,
      )
    }

    fun scale(value: JsonElement, field: String): ScaleConfig =
      when (val type = decode<TypeDto>(value, field).type) {
        "absolute" -> absoluteScale(decode(value, field), field)
        "relative" -> relativeScale(decode(value, field), field)
        else -> fail("$field.type", "expected absolute or relative, was '$type'")
      }

    fun degreeWithOctave(value: DegreeWithOctaveDto, field: String): DegreeWithOctave =
      at(field) { DegreeWithOctave(degree(value.degree, "$field.degree"), value.octave) }

    fun degreeContext(value: DegreeContextDto, field: String): DegreeContext {
      require(value.id.isNotBlank() && value.id.length <= 64, "$field.id", "is required and must be at most 64 characters")
      require(value.nodes.isNotEmpty(), "$field.nodes", "must not be empty")
      val source =
        when (value.source) {
          "built_in" -> ContextSource.BuiltIn
          "user" -> ContextSource.UserGlobal
          "local" -> ContextSource.UserLocal
          "imported" -> ContextSource.Imported
          else -> fail("$field.source", "unknown context source '${value.source}'")
        }
      return DegreeContext(
        id = value.id,
        source = source,
        nodes = value.nodes.mapIndexed { index, node -> contextNode(node, "$field.nodes[$index]") },
        name = value.name,
      )
    }

    private fun contextNode(value: DegreeContextNodeDto, field: String): DegreeContextNode {
      val sustain =
        when (value.sustain.type) {
          "endless" -> {
            require(value.sustain.durationMs == null, "$field.sustain.duration_ms", "is only valid for finite sustain")
            Sustain.Endless
          }
          "finite" -> {
            val duration = value.sustain.durationMs
              ?: fail("$field.sustain.duration_ms", "is required for finite sustain")
            require(duration > 0, "$field.sustain.duration_ms", "must be positive")
            Sustain.Finite(duration.milliseconds)
          }
          else -> fail("$field.sustain.type", "unknown sustain '${value.sustain.type}'")
        }
      val duration =
        when (value.duration.type) {
          "finite" -> {
            val count = value.duration.durationInQuestions
              ?: fail("$field.duration.duration_in_questions", "is required for finite duration")
            require(count > 0, "$field.duration.duration_in_questions", "must be positive")
            ContextDuration.Finite(count)
          }
          "immediate" -> {
            require(value.duration.durationInQuestions == null, "$field.duration.duration_in_questions", "is only valid for finite duration")
            ContextDuration.Immediate
          }
          "endless" -> {
            require(value.duration.durationInQuestions == null, "$field.duration.duration_in_questions", "is only valid for finite duration")
            ContextDuration.Endless
          }
          "same_as_scale_rotation" -> {
            require(value.duration.durationInQuestions == null, "$field.duration.duration_in_questions", "is only valid for finite duration")
            ContextDuration.SameAsScaleRotation
          }
          else -> fail("$field.duration.type", "unknown context duration '${value.duration.type}'")
        }
      val setup = value.setupMelody?.let { setup ->
        SetupMelody(
          melody = RelativeMelody(
            firstDegree = degreeWithOctave(setup.melody.firstDegree, "$field.setup_melody.melody.first_degree"),
            extraDegrees = setup.melody.extraDegrees.mapIndexed { index, degree ->
              DirectedDegree(
                this.degree(degree.degree, "$field.setup_melody.melody.extra_degrees[$index].degree"),
                direction(degree.direction, "$field.setup_melody.melody.extra_degrees[$index].direction"),
              )
            },
          ),
          repeat = SetupMelodyRepeat.entries.firstOrNull { it.name == setup.repeat }
            ?: fail("$field.setup_melody.repeat", "unknown repeat '${setup.repeat}'"),
        )
      }
      return DegreeContextNode(
        firstDegree = degreeWithOctave(value.firstDegree, "$field.first_degree"),
        extraDegrees = value.extraDegrees.mapIndexed { index, degree -> degree(degree, "$field.extra_degrees[$index]") },
        sustain = sustain,
        duration = duration,
        setupMelody = setup,
        relativeDirection = direction(value.relativeDirection, "$field.relative_direction"),
      )
    }

    private fun direction(value: String, field: String): DegreeDirection =
      DegreeDirection.entries.firstOrNull { it.name == value }
        ?: fail(field, "unknown direction '$value'")

    fun melodyStyle(value: MelodyStyleDto, field: String): MelodyStyle {
      styleHeader(value.id, value.name, value.tier, field)
      require(value.figures.isNotEmpty(), "$field.figures", "must not be empty")
      val figures = value.figures.mapIndexed { index, weighted ->
        require(weighted.weight > 0, "$field.figures[$index].weight", "must be positive")
        val figure = weighted.figure
        require(figure.values.isNotEmpty(), "$field.figures[$index].figure.values", "must not be empty")
        require(
          figure.contour.isEmpty() || figure.contour.size == figure.values.size - 1,
          "$field.figures[$index].figure.contour",
          "must be empty or contain one item per note gap",
        )
        WeightedFigure(
          figure = RhythmFigure(
            values = figure.values.mapIndexed { valueIndex, noteValue ->
              NoteValue.entries.firstOrNull { it.name == noteValue }
                ?: fail("$field.figures[$index].figure.values[$valueIndex]", "unknown note value '$noteValue'")
            },
            contour = figure.contour,
            ladder = FigureLadder.entries.firstOrNull { it.name == figure.ladder }
              ?: fail("$field.figures[$index].figure.ladder", "unknown ladder '${figure.ladder}'"),
          ),
          weight = weighted.weight,
        )
      }
      val noteWeights = value.noteWeights
      require(noteWeights.intervalWeights.all { it > 0 }, "$field.note_weights.interval_weights", "weights must be positive")
      require(noteWeights.chordToneBoost > 0, "$field.note_weights.chord_tone_boost", "must be positive")
      val degreeWeights = noteWeights.degreeWeights.mapKeys { (degree, _) -> this.degree(degree, "$field.note_weights.degree_weights.$degree") }
      require(degreeWeights.values.all { it > 0 }, "$field.note_weights.degree_weights", "weights must be positive")
      return MelodyStyle(
        id = value.id,
        name = value.name,
        description = value.description,
        tier = styleTier(value.tier, "$field.tier"),
        figures = figures,
        noteWeights = NoteWeights(noteWeights.intervalWeights, degreeWeights, noteWeights.chordToneBoost),
      )
    }

    fun chordStyle(value: ChordStyleDto, field: String): ChordStyle {
      styleHeader(value.id, value.name, value.tier, field)
      require(value.figures.isNotEmpty(), "$field.figures", "must not be empty")
      return ChordStyle(
        id = value.id,
        name = value.name,
        description = value.description,
        tier = styleTier(value.tier, "$field.tier"),
        figures = value.figures.mapIndexed { index, weighted ->
          require(weighted.weight > 0, "$field.figures[$index].weight", "must be positive")
          val figure =
            when (weighted.figure.type) {
              "free_pick" -> {
                require(weighted.figure.ladderSteps.isEmpty(), "$field.figures[$index].figure.ladder_steps", "is not valid for free_pick")
                ChordFigure.FreePick
              }
              "stacked" -> at("$field.figures[$index].figure.ladder_steps") {
                ChordFigure.Stacked(weighted.figure.ladderSteps)
              }
              else -> fail("$field.figures[$index].figure.type", "unknown chord figure '${weighted.figure.type}'")
            }
          WeightedChordFigure(figure, weighted.weight)
        },
      )
    }

    private fun styleHeader(id: String, name: String, tier: String, field: String) {
      require(id.isNotBlank(), "$field.id", "must not be blank")
      require(name.isNotBlank(), "$field.name", "must not be blank")
      styleTier(tier, "$field.tier")
    }

    private fun styleTier(value: String, field: String): StyleTier =
      StyleTier.entries.firstOrNull { it.name == value }
        ?: fail(field, "unknown style tier '$value'")
  }
}
