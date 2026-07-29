package ldv.shuuen.features.training.course.domain

import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.single.domain.SinglesLevel

data class PageMeta(val limit: Int, val offset: Int, val total: Long)

data class CoursePage(val courses: List<CourseSummary>, val meta: PageMeta)

data class CourseSummary(
  val id: Long,
  val slug: String,
  val name: String,
  val description: String,
  val author: String,
  val sortOrder: Int,
  val modes: List<CourseModeSummary>,
) {
  fun contains(mode: TrainingFlow): Boolean = modes.any { it.mode == mode }
}

data class CourseModeSummary(
  val mode: TrainingFlow,
  val name: String,
  val description: String,
  val sortOrder: Int,
  val groupCount: Int,
  val levelCount: Long,
)

data class CourseMode(
  val mode: TrainingFlow,
  val name: String,
  val description: String,
  val sortOrder: Int,
  val groupCount: Int,
  val levelCount: Long,
  val groups: List<ProgressionGroup>,
)

data class ProgressionGroup(
  val id: String,
  val name: String,
  val description: String,
  val sortOrder: Int,
  val levelCount: Long,
  val sectionCount: Long,
)

data class CourseSection(
  val libraryGroupId: Long,
  val name: String,
  val path: String,
  val depth: Int,
)

data class CourseMidiResource(
  val melodyId: Long,
  val variantId: Long,
  val downloadUrl: String,
)

data class CourseLevelNavigation(
  val previousLevelId: String?,
  val nextLevelId: String?,
  val position: Long,
  val total: Long,
)

sealed interface PlayableTrainingLevel {
  val id: String

  data class Singles(val level: SinglesLevel) : PlayableTrainingLevel {
    override val id: String get() = level.id
  }

  data class Melodies(val level: MelodiesLevel) : PlayableTrainingLevel {
    override val id: String get() = level.id
  }

  data class Chords(val level: ChordsLevel) : PlayableTrainingLevel {
    override val id: String get() = level.id
  }
}

data class CourseLevelItem(
  val reference: LevelReference.Remote,
  val playable: PlayableTrainingLevel,
  val progressionGroupId: String,
  val sortOrder: Int,
  val sections: List<CourseSection>,
  val sourceCourseId: Long,
  val mode: TrainingFlow,
  /** Present on single-level detail responses and omitted from paginated list responses. */
  val navigation: CourseLevelNavigation? = null,
) {
  val isReadOnly: Boolean = true
}

data class CourseLevelPage(val levels: List<CourseLevelItem>, val meta: PageMeta)

data class CourseLevelQuery(val levels: List<CourseLevelItem>)

class CourseMappingException(
  val levelId: String,
  val field: String,
  detail: String,
  cause: Throwable? = null,
) : IllegalArgumentException("Course level '$levelId' has invalid $field: $detail", cause)
