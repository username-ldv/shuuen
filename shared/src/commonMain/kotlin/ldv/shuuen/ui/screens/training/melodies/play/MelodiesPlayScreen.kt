package ldv.shuuen.ui.screens.training.melodies.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.domain.audio.music.Pitch
import ldv.shuuen.ui.common.LinearTrainingProgress
import ldv.shuuen.ui.common.ShuuenTopAppBar
import ldv.shuuen.ui.common.ShuuenTopAppBarType
import ldv.shuuen.ui.common.ShuuenUi
import ldv.shuuen.ui.common.StaticScreenFrame
import ldv.shuuen.ui.common.music.inputs.PianoKeyboard
import ldv.shuuen.ui.common.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.ui.common.music.inputs.rememberPianoKeyboardState

@Composable
fun MelodiesPlayScreen(
  onNavigateBack: () -> Unit,
  onLevelEnd: () -> Unit,
  viewModel: MelodiesPlayScreenViewModel,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(state.isQuizComplete) {
//    if (state.isQuizComplete) onLevelEnd()
  }

  StaticScreenFrame(
    scrollable = false,
    topBar = {
      ShuuenTopAppBar(
        title = state.title,
        onBack = onNavigateBack,
        type = ShuuenTopAppBarType.Simple,
      )
    },
  ) {
    when {
      state.isLoading ->
        CenteredMessage("Loading melody…", ShuuenUi.Muted)

      state.error != null ->
        CenteredMessage(state.error ?: "Something went wrong.", ShuuenUi.Incorrect)

      else -> {
        TrainingStatus(state)
        Spacer(Modifier.height(4.dp))
        MelodyBuffer(state)

        Spacer(Modifier.weight(1f))

        AnswerKeyboard(onGuess = viewModel::userGuessed)

        Spacer(Modifier.weight(0.5f))

        SeekBar(
          progress = state.progress,
          positionSeconds = state.positionSeconds,
          lengthSeconds = state.lengthSeconds,
          onSeek = viewModel::seekToFraction,
        )

        TransportBar(
          isPlaying = state.isPlaying,
          onRewind = viewModel::seekBackward,
          onTogglePlay = viewModel::togglePlayPause,
          onForward = viewModel::seekForward,
        )
      }
    }
  }
}

@Composable
private fun TrainingStatus(state: MelodiesPlayState) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        "${(state.answerIndex + 1).coerceAtMost(state.notes.size)}/${state.notes.size}",
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          "${state.correctAnswers}",
          color = ShuuenUi.Correct,
          style = MaterialTheme.typography.titleLarge,
        )
        Text("|", color = ShuuenUi.Dim, style = MaterialTheme.typography.titleMedium)
        Text(
          "${state.incorrectAnswers.size}",
          color = ShuuenUi.Incorrect,
          style = MaterialTheme.typography.titleLarge,
        )
      }
    }

    LinearTrainingProgress(progress = state.quizProgress)
  }
}

@Composable
private fun ColumnScope.CenteredMessage(text: String, color: Color) {
  Spacer(Modifier.weight(1f))
  Text(
    text = text,
    color = color,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
  Spacer(Modifier.weight(1f))
}

/**
 * The melody as a horizontally-scrolling strip of note cells that follows the answer cursor. The
 * lazy row keeps it cheap for long MIDI files while roughly 10–12 cells stay visible.
 */
@Composable
private fun MelodyBuffer(state: MelodiesPlayState) {
  val listState = rememberLazyListState()
  val playbackIndex = state.playbackNoteIndex
  val missedIndexes = state.incorrectAnswers.map { it.noteIndex }.toSet()
  LaunchedEffect(state.isPlaying, playbackIndex) {
    if (state.isPlaying && playbackIndex >= 0) {
      listState.animateScrollToItem((playbackIndex - 3).coerceAtLeast(0))
    }
  }
  LazyRow(
    modifier = Modifier.fillMaxWidth().height(64.dp),
    state = listState,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    itemsIndexed(state.notes) { index, _ ->
      MelodyCell(
        position = index + 1,
        label = state.answeredPitches.getOrNull(index)?.toString() ?: "-",
        answered = index < state.answerIndex,
        target = index == state.answerIndex,
        playing = index == playbackIndex && state.isPlaying,
        missed = index in missedIndexes,
      )
    }
  }
}

@Composable
private fun MelodyCell(
  position: Int,
  label: String,
  answered: Boolean,
  target: Boolean,
  playing: Boolean,
  missed: Boolean,
) {
  val shape = ShuuenUi.ControlShape
  val borderColor =
    when {
      missed && target -> ShuuenUi.Incorrect
      playing -> ShuuenUi.HairlineStrong
      target -> ShuuenUi.HairlineStrong
      else -> ShuuenUi.Hairline
    }
  val textColor =
    when {
      target -> ShuuenUi.Text
      answered -> ShuuenUi.Text
      else -> ShuuenUi.Muted
    }
  Column(
    modifier =
      Modifier.width(46.dp)
        .height(56.dp)
        .clip(shape)
        .background(
          when {
            playing -> Color.White.copy(alpha = 0.12f)
            target -> Color.White.copy(alpha = 0.08f)
            answered -> Color.White.copy(alpha = 0.04f)
            else -> Color.Transparent
          }
        )
        .border(1.dp, borderColor, shape)
        .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      "$position",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
    )
    Spacer(Modifier.weight(1f))
    Text(
      text = label,
      color = textColor,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.weight(1f))
    Box(
      modifier =
        Modifier
          .size(width = 18.dp, height = 3.dp)
          .clip(ShuuenUi.PillShape)
          .background(if (playing) ShuuenUi.Text else Color.Transparent),
    )
  }
}

/** A single-octave keyboard for answers. Playback never highlights keys here. */
@Composable
private fun AnswerKeyboard(
  onGuess: (Pitch) -> Boolean?,
) {
  val keyboardState = rememberPianoKeyboardState()
  PianoKeyboard(
    modifier = Modifier.fillMaxWidth().aspectRatio(PianoKeyboardDefaults.aspectRatio(12)),
    keyCount = 12,
    state = keyboardState,
    onKeyPressedChange = { offset, pressed ->
      if (!pressed) {
        onGuess(Pitch.fromOrdinal(offset))?.let { correct ->
          keyboardState.flash(
            index = offset,
            color = if (correct) ShuuenUi.Correct else ShuuenUi.Incorrect,
          )
        }
      }
    },
  )
}

@Composable
private fun SeekBar(
  progress: Float,
  positionSeconds: Double,
  lengthSeconds: Double,
  onSeek: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Slider(
      value = progress,
      onValueChange = onSeek,
      colors =
        SliderDefaults.colors(
          thumbColor = ShuuenUi.Text,
          activeTrackColor = ShuuenUi.Text,
          inactiveTrackColor = ShuuenUi.Hairline,
        ),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        formatTime(positionSeconds),
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        formatTime(lengthSeconds),
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun TransportBar(
  isPlaying: Boolean,
  onRewind: () -> Unit,
  onTogglePlay: () -> Unit,
  onForward: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
  ) {
    TransportIcon(Icons.Rounded.FastRewind, "Rewind", size = 32.dp, onClick = onRewind)
    PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
    TransportIcon(Icons.Rounded.FastForward, "Forward", size = 32.dp, onClick = onForward)
  }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.size(68.dp)
        .clip(CircleShape)
        .background(ShuuenUi.Inverse)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
      contentDescription = if (isPlaying) "Pause" else "Play",
      tint = ShuuenUi.OnInverse,
      modifier = Modifier.size(36.dp),
    )
  }
}

@Composable
private fun TransportIcon(
  icon: ImageVector,
  contentDescription: String,
  size: Dp,
  onClick: () -> Unit,
) {
  Icon(
    imageVector = icon,
    contentDescription = contentDescription,
    tint = ShuuenUi.Text,
    modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
  )
}

private fun formatTime(seconds: Double): String {
  val total = seconds.toInt().coerceAtLeast(0)
  val minutes = total / 60
  val secs = total % 60
  return "$minutes:${secs.toString().padStart(2, '0')}"
}
