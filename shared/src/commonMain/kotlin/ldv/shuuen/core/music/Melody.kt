package ldv.shuuen.core.music

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun constructSetupMelodyFlow(root: Pitch, melody: RelativeMelody, tempo: Int = 75): Flow<Note> {
  return flow {
    var currentNote = Note(melody.firstDegree.degree.pitch(root), melody.firstDegree.octave)
    withTiming(tempo) {
      emit(currentNote)
      delay(quarter())

      melody.extraDegrees.forEach { step ->
        val pitch = step.degree.pitch(root)
        currentNote =
          when (step.direction) {
            DegreeDirection.Up -> currentNote.next(pitch)
            DegreeDirection.Down -> currentNote.previous(pitch)
          }
        emit(currentNote)
        delay(quarter())
      }
    }
  }
}
