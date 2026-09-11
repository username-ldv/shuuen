package ldv.shuuen.core.music.generator

import ldv.shuuen.core.music.Note

interface NoteGenerator {
  fun next(): Note
}