package ldv.shuuen.core.music

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


fun constructAscSetupMelodyFlow(root: Pitch, degrees: List<Degree>, tempo: Int = 75, startingOctave: Int = 4): Flow<Note> {
  return flow {
    var currentNote: Note? = null
    withTiming(tempo) {
      degrees.forEach { d ->
        val pitch = d.pitch(root)
        currentNote = currentNote?.next(pitch) ?: Note(pitch, startingOctave)
        emit(currentNote)
        delay(quarter())
      }
    }
  }
}