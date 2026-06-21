package ldv.shuuen.features.training.melodies.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory hand-off for the current melody between the setup and play screens.
 *
 * Melodies levels are not persisted yet (unlike Singles, which round-trips through Room), so the
 * setup screen stages the built [MelodiesLevel] here and the play screen reads it back. Held as a
 * DI singleton so it survives the navigation between the two screens.
 */
class MelodiesSession {
  private val _current = MutableStateFlow<MelodiesLevel?>(null)
  val current = _current.asStateFlow()

  fun stage(level: MelodiesLevel) {
    _current.value = level
  }
}
