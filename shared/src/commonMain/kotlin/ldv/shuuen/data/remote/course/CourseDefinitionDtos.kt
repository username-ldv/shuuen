package ldv.shuuen.data.remote.course

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class TypeDto(val type: String)

@Serializable
internal data class NoteDto(val midiIndex: Int)

@Serializable
internal data class NoteRangeDto(val from: NoteDto, val to: NoteDto)

@Serializable
internal data class PitchStateDto(val pitch: String, val active: Boolean)

@Serializable
internal data class DegreeStateDto(val degree: String, val active: Boolean)

@Serializable
internal data class AbsoluteScaleConfigDto(
  val type: String? = null,
  val root: String,
  val scaleType: String,
  val pitchStates: List<PitchStateDto>,
)

@Serializable
internal data class RelativeScaleConfigDto(
  val type: String? = null,
  val scaleType: String,
  val degreeStates: List<DegreeStateDto>,
)

@Serializable
internal data class DegreeWithOctaveDto(val degree: String, val octave: Int)

@Serializable
internal data class DirectedDegreeDto(val degree: String, val direction: String)

@Serializable
internal data class RelativeMelodyDto(
  val firstDegree: DegreeWithOctaveDto,
  val extraDegrees: List<DirectedDegreeDto> = emptyList(),
)

@Serializable
internal data class SetupMelodyDto(val melody: RelativeMelodyDto, val repeat: String)

@Serializable
internal data class SustainDto(val type: String, val durationMs: Long? = null)

@Serializable
internal data class ContextDurationDto(
  val type: String,
  val durationInQuestions: Int? = null,
)

@Serializable
internal data class DegreeContextNodeDto(
  val firstDegree: DegreeWithOctaveDto,
  val extraDegrees: List<String> = emptyList(),
  val sustain: SustainDto,
  val duration: ContextDurationDto,
  val setupMelody: SetupMelodyDto? = null,
  val relativeDirection: String,
)

@Serializable
internal data class DegreeContextDto(
  val id: String,
  val source: String,
  val nodes: List<DegreeContextNodeDto>,
  val name: String? = null,
)

@Serializable
internal data class RhythmFigureDto(
  val values: List<String>,
  val contour: List<Int?> = emptyList(),
  val ladder: String,
)

@Serializable
internal data class WeightedRhythmFigureDto(
  val figure: RhythmFigureDto,
  val weight: Double,
)

@Serializable
internal data class NoteWeightsDto(
  val intervalWeights: List<Double> = emptyList(),
  val degreeWeights: Map<String, Double> = emptyMap(),
  val chordToneBoost: Double,
)

@Serializable
internal data class MelodyStyleDto(
  val id: String,
  val name: String,
  val description: String = "",
  val tier: String,
  val figures: List<WeightedRhythmFigureDto>,
  val noteWeights: NoteWeightsDto,
)

@Serializable
internal data class ChordFigureDto(
  val type: String,
  val ladderSteps: List<Int> = emptyList(),
)

@Serializable
internal data class WeightedChordFigureDto(
  val figure: ChordFigureDto,
  val weight: Double,
)

@Serializable
internal data class ChordStyleDto(
  val id: String,
  val name: String,
  val description: String = "",
  val tier: String,
  val figures: List<WeightedChordFigureDto>,
)

@Serializable
internal data class SinglesDefinitionDto(
  val levelConfig: JsonElement,
  val context: DegreeContextDto? = null,
  val questionsNumber: Int? = null,
  val range: NoteRangeDto,
)

@Serializable
internal data class SinglesAbsoluteConfigDto(
  val type: String,
  val scales: List<AbsoluteScaleConfigDto>,
  val rotateEveryQuestions: Int? = null,
  val tuneInconsistencyCents: Int = 0,
)

@Serializable
internal data class SinglesRelativeConfigDto(
  val type: String,
  val scaleConfig: RelativeScaleConfigDto,
  val rotateEveryQuestions: Int? = null,
  val tuneInconsistencyCents: Int = 0,
)

@Serializable
internal data class MelodiesDefinitionDto(
  val config: JsonElement,
  val context: DegreeContextDto? = null,
)

@Serializable
internal data class RandomMelodyConfigDto(
  val type: String,
  val scaleConfig: JsonElement,
  val questionsNumber: Int? = null,
  val notesPerSequence: Int? = null,
  val tempo: Int,
  val range: NoteRangeDto,
  val rotateEveryQuestions: Int? = null,
  val melodyStyle: MelodyStyleDto? = null,
  val tuneInconsistencyCents: Int = 0,
)

@Serializable
internal data class MidiFileReferenceDto(
  val type: String,
  val melodyId: Long? = null,
  val variantId: Long? = null,
  val path: String? = null,
  val fileName: String,
)

@Serializable
internal data class MidiMelodyConfigDto(
  val type: String,
  val file: MidiFileReferenceDto,
  val useOriginalVelocities: Boolean = false,
)

@Serializable
internal data class ChordSizeRangeDto(val min: Int, val max: Int)

@Serializable
internal data class ChordsDefinitionDto(
  val levelConfig: JsonElement,
  val context: DegreeContextDto? = null,
  val questionsNumber: Int? = null,
  val range: NoteRangeDto,
  val chordSize: ChordSizeRangeDto,
  val sustainNotes: Boolean,
  val answerOrder: String,
)

@Serializable
internal data class ChordsAbsoluteConfigDto(
  val type: String,
  val scales: List<AbsoluteScaleConfigDto>,
  val rotateEveryQuestions: Int? = null,
  val chordStyle: ChordStyleDto? = null,
)

@Serializable
internal data class ChordsRelativeConfigDto(
  val type: String,
  val scaleConfig: RelativeScaleConfigDto,
  val rotateEveryQuestions: Int? = null,
  val chordStyle: ChordStyleDto? = null,
)
