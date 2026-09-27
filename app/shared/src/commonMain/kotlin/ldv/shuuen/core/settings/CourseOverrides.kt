package ldv.shuuen.core.settings

import kotlinx.serialization.Serializable

/**
 * Local replacements for level parameters across one course, chosen in its course settings sheet.
 * They stay on this device and are applied when a course level is resolved for play; the course's
 * own level definitions are never changed.
 */
@Serializable
data class CourseOverrides(
  /** Replaces the tune inconsistency of every level in the course; null keeps each level's own. */
  val tuneInconsistencyCents: Int? = null,
)
