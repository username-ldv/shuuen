package ldv.shuuen.data.remote.course

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class DataEnvelopeDto<T>(val data: T)

@Serializable
internal data class PageEnvelopeDto<T>(val data: List<T>, val meta: PageMetaDto)

@Serializable
internal data class PageMetaDto(val limit: Int, val offset: Int, val total: Long)

@Serializable
internal data class CourseDto(
  val id: Long,
  val libraryGroupId: Long,
  val slug: String,
  val name: String,
  val description: String,
  val author: String,
  val isPublic: Boolean,
  val sortOrder: Int,
  val structureSource: String,
  val modeCount: Int,
  val progressionGroupCount: Int,
  val levelCount: Long,
  val modes: List<CourseModeDto> = emptyList(),
)

@Serializable
internal data class CourseModeDto(
  val mode: String,
  val name: String,
  val description: String,
  val sortOrder: Int,
  val groupCount: Int,
  val levelCount: Long,
  val groups: List<ProgressionGroupDto> = emptyList(),
)

@Serializable
internal data class ProgressionGroupDto(
  val id: String,
  val libraryGroupId: Long,
  val name: String,
  val description: String,
  val sortOrder: Int,
  val levelCount: Long,
  val sectionCount: Long,
  val blueprint: Boolean,
)

@Serializable
internal data class CourseSectionDto(
  val libraryGroupId: Long,
  val name: String,
  val path: String,
  val depth: Int,
)

@Serializable
internal data class CourseMidiResourceDto(
  val melodyId: Long,
  val variantId: Long,
  val downloadUrl: String,
)

@Serializable
internal data class CourseLevelDto(
  val id: String,
  val progressionGroupId: String,
  val name: String,
  val source: String,
  val definition: JsonElement,
  val sortOrder: Int,
  val isPublic: Boolean,
  val midi: CourseMidiResourceDto? = null,
  val sections: List<CourseSectionDto>,
)

@Serializable
internal data class QueryCourseLevelsRequestDto(val ids: List<String>)
