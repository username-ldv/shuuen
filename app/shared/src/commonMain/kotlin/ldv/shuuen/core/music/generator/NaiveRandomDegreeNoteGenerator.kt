package ldv.shuuen.core.music.generator

import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch

class NaiveRandomDegreeNoteGenerator(
  val root: Pitch, val range: NoteRange, val allowedDegrees: List<Degree>
) : NoteGenerator {
  private val allowedNotes = (range.from..range.to).filter {
    allowedDegrees.any { degree -> degree == root.asRoot(it.pitch) }
  }

  override fun next(): Note {
    return allowedNotes.random()
  }
}