package ldv.shuuen.features.training.melodies.play

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ldv.shuuen.core.settings.InputComponent
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.ui.components.LinearTrainingProgress
import ldv.shuuen.core.ui.components.MidiKeyboardBadge
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.SoftControl
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.music.inputs.FifthsCircle
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboard
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.core.ui.components.music.inputs.rememberFifthsCircleState
import ldv.shuuen.core.ui.components.music.inputs.rememberPianoKeyboardState
import ldv.shuuen.features.training.common.components.CircleCenterIconButton
import ldv.shuuen.features.training.common.components.circleItemNames
import ldv.shuuen.features.training.common.components.circleTopItem
import ldv.shuuen.features.training.common.components.inputLabelForPitch
import ldv.shuuen.features.training.common.components.pitchToItemIndex

@Composable
fun MelodiesPlayScreen(
  onNavigateBack: () -> Unit,
  onLevelEnd: (sessionId: String) -> Unit,
  viewModel: MelodiesPlayScreenViewModel,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val inputMethod by viewModel.inputMethod.collectAsStateWithLifecycle()
  val musicLabels by viewModel.musicLabels.collectAsStateWithLifecycle()

  LaunchedEffect(state.completion) {
    // A session that saved nothing (e.g. finished early before any answer) has no results to
    // show; just leave the play screen.
    state.completion?.let { it.sessionId?.let(onLevelEnd) ?: onNavigateBack() }
  }

  val quizRunning = !state.isLoading && state.error == null
  StaticScreenFrame(
    scrollable = false,
    // The circle input is full-bleed (it must reach the screen edges to stay tappable there), so
    // the frame adds no horizontal padding; each child below applies its own where needed.
    horizontalPadding = 0.dp,
    bottomPadding = MelodiesBottomPadding,
    verticalSpacing = MelodiesVerticalSpacing,
    topBar = {
      ShuuenTopAppBar(
        title = state.title,
        onBack = onNavigateBack,
        trailingIcon = if (quizRunning) Icons.Rounded.Flag else null,
        onTrailingClick = { viewModel.finishEarly() },
        statusContent = { MidiKeyboardBadge() },
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
        TrainingStatus(state, modifier = Modifier.padding(horizontal = ScreenHorizontalPadding))
        MelodyBuffer(
          state = state,
          inputMode = inputMethod.mode,
          musicLabels = musicLabels,
          modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
        )

        val keyboardState = rememberPianoKeyboardState()
        val circleState = rememberFifthsCircleState()

        // The setup-melody flow emits absolute pitches; the index meaning depends on the active
        // input method, and the relative mapping needs the current root. Track both without
        // restarting the collector each time they change.
        val currentInputMethod by rememberUpdatedState(inputMethod)
        val currentRoot by rememberUpdatedState(state.root)

        LaunchedEffect(Unit) {
          launch {
            viewModel.setupMelodyFlashes.collect { req ->
              val method = currentInputMethod
              val index = pitchToItemIndex(req.pitch, method.mode, currentRoot)
              when (method.component) {
                InputComponent.Piano ->
                  keyboardState.flash(index, req.color, holdMillis = 520, attackMillis = 80, releaseMillis = 300)
                InputComponent.Circle ->
                  circleState.flash(index, req.color, holdMillis = 520, attackMillis = 80, releaseMillis = 300)
              }
            }
          }
          // MIDI keyboard guesses flash like taps: default (short) flash timings.
          launch {
            viewModel.midiGuessFlashes.collect { req ->
              val method = currentInputMethod
              val index = pitchToItemIndex(req.pitch, method.mode, currentRoot)
              when (method.component) {
                InputComponent.Piano -> keyboardState.flash(index, req.color)
                InputComponent.Circle -> circleState.flash(index, req.color)
              }
            }
          }
        }

        // Both components reduce a tapped item to a guess via userGuessed(index, mode); the flash
        // uses the tapped index directly, so no extra mapping is needed for feedback.
        fun handleGuess(index: Int, flashOn: (Int, Color) -> Unit) {
          viewModel.userGuessed(index, inputMethod.mode)?.let { correct ->
            flashOn(index, if (correct) ShuuenUi.Correct else ShuuenUi.Incorrect)
          }
        }

        when (inputMethod.component) {
          InputComponent.Piano -> {
            Spacer(Modifier.weight(1f))
            PianoKeyboard(
              modifier = Modifier
                .padding(horizontal = ScreenHorizontalPadding)
                .fillMaxWidth()
                .aspectRatio(PianoKeyboardDefaults.aspectRatio(12)),
              keyCount = 12,
              state = keyboardState,
              onKeyPressedChange = { index, pressed ->
                if (!pressed) handleGuess(index) { i, c -> keyboardState.flash(i, c) }
              },
            )
            Spacer(Modifier.weight(0.34f))
          }

          // The circle is full-bleed: it fills the whole flexible region so the empty space
          // around the ring is part of the touch surface (same as the Singles play screen).
          InputComponent.Circle ->
            FifthsCircle(
              modifier = Modifier.fillMaxWidth().weight(1f),
              itemNames =
                circleItemNames(
                  inputMethod.mode,
                  state.root,
                  state.accidentalType,
                  musicLabels,
                ),
              rotateItemToTop = circleTopItem(inputMethod, state.root),
              state = circleState,
              onItemPressedChange = { index, pressed ->
                if (!pressed) handleGuess(index) { i, c -> circleState.flash(i, c) }
              },
              dotEdgePadding = 16.dp,
              // The transport lives in the ring's empty middle, so no bottom bar steals height
              // from the circle in any level configuration.
              centerContent = { CircleCenterControls(state, viewModel) },
            )
        }

        if (inputMethod.component == InputComponent.Piano) {
          Column(modifier = Modifier.padding(horizontal = ScreenHorizontalPadding)) {
            when {
              state.mode == MelodiesPlayMode.Midi -> {
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

              // The endless stream has no destination to seek toward: just pause and rewind.
              state.isEndless ->
                EndlessTransportBar(
                  isPlaying = state.isPlayingSequence,
                  onRewind = viewModel::seekBackward,
                  onTogglePlay = viewModel::togglePlayPause,
                )

              else ->
                RewindBar(
                  onRewind = viewModel::rewindSequence,
                  onRepeatMelody = viewModel::playSetupMelody,
                )
            }
          }
        }
      }
    }
  }
}

/**
 * Horizontal inset applied to the non-circle children. The frame itself adds no horizontal padding
 * so the circle can be full-bleed; padded children re-apply this to keep their usual margins.
 */
private val ScreenHorizontalPadding = 20.dp
private val MelodiesVerticalSpacing = 8.dp
private val MelodiesBottomPadding = 8.dp
private val MelodyBufferHeight = 56.dp
private val MelodyCellWidth = 44.dp
private val MelodyCellHeight = 48.dp
private val BottomControlHeight = 52.dp
private val PlayPauseButtonSize = 64.dp
private val PlayPauseIconSize = 34.dp
private val TransportIconSize = 32.dp
private val TransportButtonGap = 28.dp

// Compact metrics for the transport cluster inside the fifths circle's middle: everything must
// stay clear of the ring's labels even on narrow screens.
private val CenterTransportIconSize = 30.dp
private val CenterTransportGap = 20.dp
private val CenterPlayPauseButtonSize = 56.dp
private val CenterPlayPauseIconSize = 30.dp
private val CenterSeekBarWidth = 172.dp
private val CenterSeekBarHeight = 30.dp

@Composable
private fun TrainingStatus(state: MelodiesPlayState, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
    ) {
      val counter =
        when {
          state.mode == MelodiesPlayMode.Midi ->
            "${(state.answerIndex + 1).coerceAtMost(state.notes.size)}/${state.notes.size}"

          state.isEndless -> "${state.answerIndex + 1}/∞"

          else ->
            state.questionsNumber?.let {
              "${state.questionNumber.coerceAtMost(it)}/$it"
            } ?: "${state.questionNumber}/∞"
        }
      Text(
        counter,
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

    // An endless stream or an unlimited-questions session has no end to progress toward; the bar
    // still reserves its slot so the input area below keeps the same size in every configuration.
    val hasProgressTarget =
      state.mode == MelodiesPlayMode.Midi || (!state.isEndless && state.questionsNumber != null)
    LinearTrainingProgress(
      progress = state.quizProgress,
      modifier = Modifier.alpha(if (hasProgressTarget) 1f else 0f),
    )
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
 * lazy row keeps it cheap for long MIDI files and the endless stream; short sequences sit centered
 * within it.
 */
@Composable
private fun MelodyBuffer(
  state: MelodiesPlayState,
  inputMode: InputMode,
  musicLabels: MusicLabelSettings,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val playbackIndex = state.playbackNoteIndex
  val missedIndexes = state.missedIndexes
  LaunchedEffect(state.isPlaybackActive, playbackIndex) {
    if (state.isPlaybackActive && playbackIndex >= 0) {
      listState.animateScrollToItem((playbackIndex - 5).coerceAtLeast(0))
    }
  }
  LazyRow(
    modifier = modifier.fillMaxWidth().height(MelodyBufferHeight),
    state = listState,
    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    itemsIndexed(state.notes) { index, _ ->
      val label =
        state.answeredPitches.getOrNull(index)?.let { pitch ->
          inputLabelForPitch(
            pitch = pitch,
            mode = inputMode,
            root = state.root,
            accidentalType = state.accidentalType,
            musicLabels = musicLabels,
          )
        } ?: "-"
      MelodyCell(
        position = index + 1,
        label = label,
        answered = index < state.answerIndex,
        target = index == state.answerIndex,
        playing = index == playbackIndex && state.isPlaybackActive,
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
      Modifier.width(MelodyCellWidth)
        .height(MelodyCellHeight)
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
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      "$position",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
    )
    Text(
      text = label,
      color = textColor,
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Box(
      modifier =
        Modifier
          .size(width = 18.dp, height = 3.dp)
          .clip(ShuuenUi.PillShape)
          .background(if (playing) ShuuenUi.Text else Color.Transparent),
    )
  }
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
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
  ) {
    TransportIcon(Icons.Rounded.FastRewind, "Rewind", size = TransportIconSize, onClick = onRewind)
    PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
    TransportIcon(Icons.Rounded.FastForward, "Forward", size = TransportIconSize, onClick = onForward)
  }
}

/** Endless-stream transport: pause/resume and rewind — there is no forward in an endless melody. */
@Composable
private fun EndlessTransportBar(
  isPlaying: Boolean,
  onRewind: () -> Unit,
  onTogglePlay: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxWidth().height(PlayPauseButtonSize),
    contentAlignment = Alignment.Center,
  ) {
    PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
    TransportIcon(
      icon = Icons.Rounded.FastRewind,
      contentDescription = "Rewind",
      size = TransportIconSize,
      modifier =
        Modifier
          .align(Alignment.Center)
          .offset(x = -(PlayPauseButtonSize / 2f + TransportButtonGap + TransportIconSize / 2f)),
      onClick = onRewind,
    )
  }
}

/**
 * Mode-specific transport controls rendered inside the fifths circle's empty middle, replacing the
 * bottom bars of the piano layout so the circle keeps its full size in every level configuration.
 */
@Composable
private fun CircleCenterControls(
  state: MelodiesPlayState,
  viewModel: MelodiesPlayScreenViewModel,
) {
  when {
    state.mode == MelodiesPlayMode.Midi ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(CenterTransportGap),
        ) {
          TransportIcon(
            icon = Icons.Rounded.FastRewind,
            contentDescription = "Rewind",
            size = CenterTransportIconSize,
            onClick = viewModel::seekBackward,
          )
          PlayPauseButton(
            isPlaying = state.isPlaying,
            size = CenterPlayPauseButtonSize,
            iconSize = CenterPlayPauseIconSize,
            onClick = viewModel::togglePlayPause,
          )
          TransportIcon(
            icon = Icons.Rounded.FastForward,
            contentDescription = "Forward",
            size = CenterTransportIconSize,
            onClick = viewModel::seekForward,
          )
        }
        Slider(
          value = state.progress,
          onValueChange = viewModel::seekToFraction,
          modifier = Modifier.width(CenterSeekBarWidth).height(CenterSeekBarHeight),
          colors =
            SliderDefaults.colors(
              thumbColor = ShuuenUi.Text,
              activeTrackColor = ShuuenUi.Text,
              inactiveTrackColor = ShuuenUi.Hairline,
            ),
        )
        Text(
          "${formatTime(state.positionSeconds)} / ${formatTime(state.lengthSeconds)}",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.bodySmall,
        )
      }

    // The endless stream has no destination to seek toward: just pause and rewind.
    state.isEndless ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CenterTransportGap),
      ) {
        TransportIcon(
          icon = Icons.Rounded.FastRewind,
          contentDescription = "Rewind",
          size = CenterTransportIconSize,
          onClick = viewModel::seekBackward,
        )
        PlayPauseButton(
          isPlaying = state.isPlayingSequence,
          size = CenterPlayPauseButtonSize,
          iconSize = CenterPlayPauseIconSize,
          onClick = viewModel::togglePlayPause,
        )
        // Mirrors the rewind icon so the play button sits dead-center in the ring.
        Spacer(Modifier.size(CenterTransportIconSize))
      }

    else ->
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircleCenterIconButton(
          icon = Icons.Rounded.FastRewind,
          contentDescription = "Rewind",
          onClick = viewModel::rewindSequence,
        )
        CircleCenterIconButton(
          icon = Icons.Rounded.MusicNote,
          contentDescription = "Repeat setup melody",
          tint = ShuuenUi.Muted,
          onClick = viewModel::playSetupMelody,
        )
      }
  }
}

/**
 * Finite Random mode's bottom bar, arranged like the Singles one: rewind the current sequence,
 * plus a music-note button that replays the context's setup melody.
 */
@Composable
private fun RewindBar(onRewind: () -> Unit, onRepeatMelody: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SoftControl(
      modifier = Modifier.weight(1.8f).height(BottomControlHeight),
      onClick = onRewind,
    ) {
      Icon(
        imageVector = Icons.Rounded.FastRewind,
        contentDescription = null,
        tint = ShuuenUi.Text,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = "Back 4",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    SoftControl(
      modifier = Modifier.width(80.dp).height(BottomControlHeight),
      onClick = onRepeatMelody,
    ) {
      Icon(
        Icons.Rounded.MusicNote,
        contentDescription = "Repeat setup melody",
        tint = ShuuenUi.Muted,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
private fun PlayPauseButton(
  isPlaying: Boolean,
  onClick: () -> Unit,
  size: Dp = PlayPauseButtonSize,
  iconSize: Dp = PlayPauseIconSize,
) {
  Box(
    modifier =
      Modifier.size(size)
        .clip(CircleShape)
        .background(ShuuenUi.Inverse)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
      contentDescription = if (isPlaying) "Pause" else "Play",
      tint = ShuuenUi.OnInverse,
      modifier = Modifier.size(iconSize),
    )
  }
}

@Composable
private fun TransportIcon(
  icon: ImageVector,
  contentDescription: String,
  size: Dp,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Icon(
    imageVector = icon,
    contentDescription = contentDescription,
    tint = ShuuenUi.Text,
    modifier = modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
  )
}

private fun formatTime(seconds: Double): String {
  val total = seconds.toInt().coerceAtLeast(0)
  val minutes = total / 60
  val secs = total % 60
  return "$minutes:${secs.toString().padStart(2, '0')}"
}
