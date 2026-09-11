package ldv.shuuen.data.remote.course

import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelPage
import ldv.shuuen.features.training.course.domain.CourseLevelQuery
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CourseModeSummary
import ldv.shuuen.features.training.course.domain.CoursePage
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PageMeta
import ldv.shuuen.features.training.course.domain.ProgressionGroup
import ldv.shuuen.features.training.course.domain.apiName

internal class CourseRepositoryImpl(
  private val api: CourseApi,
  private val mapper: CourseDefinitionMapper,
) : CourseRepository {
  override suspend fun listCourses(limit: Int, offset: Int): CoursePage {
    require(limit in 1..200) { "Course page limit must be in 1..200." }
    require(offset >= 0) { "Course page offset cannot be negative." }
    val response = api.listCourses(limit, offset)
    return CoursePage(response.data.map(::mapCourse), response.meta.toDomain())
  }

  override suspend fun getCourse(courseId: Long): CourseSummary {
    require(courseId > 0) { "Course ID must be positive." }
    return mapCourse(api.getCourse(courseId).data)
  }

  override suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode {
    require(courseId > 0) { "Course ID must be positive." }
    val dto = api.getCourseMode(courseId, mode).data
    val responseMode = dto.mode.toTrainingFlow("course mode")
    require(responseMode == mode) { "The backend returned ${dto.mode} for ${mode.apiName}." }
    return CourseMode(
      mode = responseMode,
      name = dto.name,
      description = dto.description,
      sortOrder = dto.sortOrder,
      groupCount = dto.groupCount,
      levelCount = dto.levelCount,
      groups = dto.groups
        .map {
          ProgressionGroup(
            id = it.id,
            name = it.name,
            description = it.description,
            sortOrder = it.sortOrder,
            levelCount = it.levelCount,
            sectionCount = it.sectionCount,
          )
        }
        .sortedWith(compareBy(ProgressionGroup::sortOrder, ProgressionGroup::name, ProgressionGroup::id)),
    )
  }

  override suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): CourseLevelPage {
    require(courseId > 0) { "Course ID must be positive." }
    require(groupId.isNotBlank()) { "Progression group ID cannot be blank." }
    require(limit in 1..200) { "Course level page limit must be in 1..200." }
    require(offset >= 0) { "Course level page offset cannot be negative." }
    val response = api.getLevels(courseId, mode, groupId, limit, offset)
    return CourseLevelPage(
      levels = response.data.map { mapper.map(courseId, mode, it) },
      meta = response.meta.toDomain(),
    )
  }

  override suspend fun getLevel(reference: LevelReference.Remote) =
    mapper.map(
      reference.courseId,
      reference.mode,
      api.getLevel(reference.courseId, reference.mode, reference.levelId).data,
    )

  override suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery {
    require(levelIds.size in 1..200) { "Course level query must contain 1..200 IDs." }
    require(levelIds.all { it.isNotBlank() } && levelIds.distinct().size == levelIds.size) {
      "Course level query IDs must be non-blank and unique."
    }
    return CourseLevelQuery(
      api.queryLevels(courseId, mode, levelIds).data.map { mapper.map(courseId, mode, it) }
    )
  }

  private fun mapCourse(dto: CourseDto): CourseSummary =
    CourseSummary(
      id = dto.id,
      slug = dto.slug,
      name = dto.name,
      description = dto.description,
      author = dto.author,
      sortOrder = dto.sortOrder,
      modes = dto.modes
        .map {
          CourseModeSummary(
            mode = it.mode.toTrainingFlow("course ${dto.id} mode"),
            name = it.name,
            description = it.description,
            sortOrder = it.sortOrder,
            groupCount = it.groupCount,
            levelCount = it.levelCount,
          )
        }
        .sortedBy { it.sortOrder },
    )

  private fun PageMetaDto.toDomain(): PageMeta = PageMeta(limit, offset, total)

  private fun String.toTrainingFlow(field: String): TrainingFlow =
    TrainingFlow.entries.firstOrNull { it.apiName == this }
      ?: error("Unsupported $field '$this'.")
}
