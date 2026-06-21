package ldv.shuuen.core.music.generator

import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch

class NaiveRandomNoteGenerator(val range: NoteRange, val allowedPitches: List<Pitch>) :
  NoteGenerator {
  private val allowedNotes = (range.from..range.to).filter {
    allowedPitches.any { pitch -> pitch == it.pitch }
  }

  override fun next(): Note {
    return allowedNotes.random()
  }
}