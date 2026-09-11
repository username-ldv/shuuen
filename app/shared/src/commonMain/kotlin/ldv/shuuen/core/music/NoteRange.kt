package ldv.shuuen.core.music

import kotlinx.serialization.Serializable

@Serializable
data class NoteRange(val from: Note, val to: Note) {
  fun toPair(): Pair<Note, Note> = this.from to this.to

  companion object {
    fun fromPair(pair: Pair<Note, Note>): NoteRange = NoteRange(pair.first, pair.second)
  }
}

fun Pair<Note, Note>.toNoteRange(): NoteRange = NoteRange.fromPair(this)
