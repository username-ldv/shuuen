package ldv.shuuen.features.training.course.domain

import ldv.shuuen.features.training.common.TrainingFlow

interface CourseRepository {
  suspend fun listCourses(limit: Int = 50, offset: Int = 0): CoursePage

  suspend fun getCourse(courseId: Long): CourseSummary

  suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode

  suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int = 20,
    offset: Int = 0,
  ): CourseLevelPage

  suspend fun getLevel(reference: LevelReference.Remote): CourseLevelItem

  suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery
}
