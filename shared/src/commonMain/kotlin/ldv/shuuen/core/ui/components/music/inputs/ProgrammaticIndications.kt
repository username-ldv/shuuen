package ldv.shuuen.core.ui.components.music.inputs

sealed interface ProgrammaticIndications {
  val index: Int
  /**
   * null = persistent while this object is present in programmaticIndications.
   * non-null = animate for this many milliseconds.
   */
  val durationMillis: Long?


}