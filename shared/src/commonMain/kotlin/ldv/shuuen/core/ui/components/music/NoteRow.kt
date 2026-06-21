package ldv.shuuen.core.ui.components.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.ui.components.BoxedListItem
import ldv.shuuen.core.ui.theme.ShuuenTheme

// todo: use Note.pianoKeyNumber
@Composable
fun NoteRow(
  value: Note,
  // lowest, highest
  bounds: Pair<Note, Note> = Note(Pitch.A, 0) to Note(Pitch.C, 8),
  nameGenerator: ((note: Note) -> String)? = null,
  itemSize: Dp = 50.dp,
  onChoose: (note: Note) -> Unit = {},
) {
  val selectedIndex = value.midiIndex - Note.MidiOffset
  BoxWithConstraints {
    val density = LocalDensity.current

    val itemWidthPx = with(density) { itemSize.roundToPx() }
    val viewportWidthPx = with(density) { maxWidth.roundToPx() }
    val centerOffsetPx = -(viewportWidthPx / 2 - itemWidthPx / 2)

    val listState = rememberLazyListState(
      initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0),
      initialFirstVisibleItemScrollOffset = centerOffsetPx
    )
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      for (note in bounds.first..bounds.second) {
        item {
          BoxedListItem(
            label = nameGenerator?.invoke(note) ?: note.toString(),
            active = value.midiIndex == note.midiIndex,
            onClicked = { onChoose(note) },
            itemSize = itemSize
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun NoteRowPreview() {
  ShuuenTheme {
    NoteRow(Note(Pitch.C))
  }
}