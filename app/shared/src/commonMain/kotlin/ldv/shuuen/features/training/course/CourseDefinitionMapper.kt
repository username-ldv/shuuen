package ldv.shuuen.features.training.course

import kotlinx.serialization.json.Json
import ldv.shuuen.core.network.ApiJsonQualifier
import ldv.shuuen.features.training.level.LevelDefinitionCodec
import ldv.shuuen.features.training.level.LevelDefinitionException
import ldv.shuuen.features.training.level.LevelMidiResource
import ldv.shuuen.features.training.level.LevelSource
import ldv.shuuen.features.training.level.TrainingFlow
import org.koin.core.annotation.Named

internal class CourseDefinitionMapper(@Named(ApiJsonQualifier) json: Json) {
  private val definitions = LevelDefinitionCodec(json)

  fun map(courseId: Long, mode: TrainingFlow, dto: CourseLevelDto): CourseLevelItem {
    val context = MappingContext(dto.id)
    return context.at("definition") {
      context.require(
        dto.id.isNotBlank() && dto.id.length <= 64,
        "id",
        "is required and must be at most 64 characters",
      )
      val reference = LevelReference.Remote(courseId, mode, dto.id)
      val source = context.levelSource(dto.source, "source")
      val playable =
        try {
          definitions.decode(
            flow = mode,
            id = reference.encoded,
            name = dto.name,
            source = source,
            definition = dto.definition,
            midi = dto.midi?.let { LevelMidiResource(it.melodyId, it.variantId, it.downloadUrl) },
          )
        } catch (error: LevelDefinitionException) {
          throw CourseMappingException(dto.id, error.field, error.detail, error.cause)
        }
      CourseLevelItem(
        reference = reference,
        playable = playable,
        progressionGroupId = dto.progressionGroupId.nonBlank(context, "progression_group_id"),
        sortOrder = dto.sortOrder,
        sections =
          dto.sections.mapIndexed { index, section ->
            context.require(
              section.libraryGroupId > 0,
              "sections[$index].library_group_id",
              "must be positive",
            )
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

  private fun String.nonBlank(context: MappingContext, field: String): String {
    context.require(isNotBlank(), field, "must not be blank")
    return this
  }

  private class MappingContext(private val levelId: String) {
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

    fun require(value: Boolean, field: String, detail: String) {
      if (!value) fail(field, detail)
    }

    fun levelSource(value: String, field: String): LevelSource =
      LevelSource.entries.firstOrNull { it.dbValue == value }
        ?: fail(field, "unknown level source '$value'")
  }
}
