package ldv.shuuen.features.training.chords.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.result.ResponseState
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
import kotlinx.coroutines.launch
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.core.ui.components.music.inputs.rememberFifthsCircleState
import ldv.shuuen.core.ui.components.music.inputs.rememberPianoKeyboardState
import ldv.shuuen.features.training.chords.domain.ChordAnswerOrder
import ldv.shuuen.features.training.common.components.CircleCenterIconButton
import ldv.shuuen.features.training.common.components.circleItemNames
import ldv.shuuen.features.training.common.components.circleTopItem
import ldv.shuuen.features.training.common.components.inputLabelForPitch
import ldv.shuuen.features.training.common.components.pitchToItemIndex

@Composable
fun ChordsPlayScreen(
    onNavigateBack: () -> Unit,
    onLevelEnd: (sessionId: String) -> Unit,
    viewModel: ChordsPlayScreenViewModel,
) {
  val screenState by viewModel.state.collectAsStateWithLifecycle()
  val inputMethod by viewModel.inputMethod.collectAsStateWithLifecycle()
  val musicLabels by viewModel.musicLabels.collectAsStateWithLifecycle()
  val title =
      when (val level = screenState.levelData) {
        is ResponseState.Loading -> "Loading..."
        is ResponseState.Error -> "Error"
        is ResponseState.Success -> level.result.name
      }

  LaunchedEffect(screenState.phase) {
    when (val phase = screenState.phase) {
      // A session that saved nothing (e.g. finished early before any answer) has no results to
      // show; just leave the play screen.
      is ChordsQuizPhase.Complete -> phase.sessionId?.let(onLevelEnd) ?: onNavigateBack()
      else -> Unit
    }
  }

  val circleInput = inputMethod.component == InputComponent.Circle
  StaticScreenFrame(
      scrollable = false,
      // The circle input is full-bleed (it must reach the screen edges to stay tappable there), so
      // the frame adds no horizontal padding; each child below applies its own where needed.
      horizontalPadding = 0.dp,
      topBar = {
        ShuuenTopAppBar(
            title = title,
            onBack = onNavigateBack,
            // The circle layout has no bottom bar; finishing early lives up here instead, matching
            // the melodies play screen.
            trailingIcon =
                if (circleInput && screenState.quizState != null) Icons.Rounded.Flag else null,
            onTrailingClick = { viewModel.finishEarly() },
            statusContent = { MidiKeyboardBadge() },
            type = ShuuenTopAppBarType.Simple,
        )
      },
  ) {
    screenState.quizState?.let {
      TrainingStatus(
          it.currentQuestionNumber,
          it.correctAnswers,
          it.incorrectAnswers.size,
          it.questionsNumber,
          modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
      )
      ChordBuffer(
          quizState = it,
          answerOrder = (screenState.levelData as? ResponseState.Success)?.result?.answerOrder,
          inputMode = inputMethod.mode,
          musicLabels = musicLabels,
          modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
      )
    }

    val keyboardState = rememberPianoKeyboardState()
    val circleState = rememberFifthsCircleState()

    // The setup-melody flow emits absolute pitches; the index meaning depends on the active input
    // method, and the relative mapping needs the current root. Track both without restarting the
    // collector each time they change.
    val currentInputMethod by rememberUpdatedState(inputMethod)
    val currentRoot by rememberUpdatedState(screenState.quizState?.root)

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

    // Both components reduce a tapped item to a guess via userGuessed(index, mode); the flash uses
    // the tapped index directly, so no extra mapping is needed for feedback. A re-press of an
    // already-found pitch is ignored: no flash either way.
    fun handleGuess(index: Int, flashOn: (Int, Color) -> Unit) {
      if (screenState.phase != ChordsQuizPhase.AwaitingAnswer) return
      when (viewModel.userGuessed(index, inputMethod.mode)) {
        ChordGuessResult.Correct -> flashOn(index, ShuuenUi.Correct)
        ChordGuessResult.Incorrect -> flashOn(index, ShuuenUi.Incorrect)
        ChordGuessResult.Ignored, null -> Unit
      }
    }

    when (inputMethod.component) {
      InputComponent.Piano -> {
        Spacer(Modifier.weight(1f))
        // pressedKeyColors stays at its neutral default (plain touch feedback); all color comes
        // from flashes.
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

      // The circle is full-bleed: it fills the whole flexible region (full width to the screen
      // edges, all remaining height) so the empty space around the ring — above, below, and to the
      // sides — is part of the touch surface. Items sit at the edge (dotEdgePadding = 0) yet stay
      // hittable right up to the screen edge.
      InputComponent.Circle ->
          FifthsCircle(
              modifier = Modifier.fillMaxWidth().weight(1f),
              itemNames =
                  circleItemNames(
                      inputMethod.mode,
                      screenState.quizState?.root,
                      screenState.quizState?.accidentalType,
                      musicLabels,
                  ),
              rotateItemToTop = circleTopItem(inputMethod, screenState.quizState?.root),
              state = circleState,
              onItemPressedChange = { index, pressed ->
                if (!pressed) handleGuess(index) { i, c -> circleState.flash(i, c) }
              },
              dotEdgePadding = 16.dp,
              // The repeat controls live in the ring's empty middle, so no bottom bar steals
              // height from the circle.
              centerContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                  CircleCenterIconButton(
                      icon = Icons.Rounded.Replay,
                      contentDescription = "Repeat chord",
                      onClick = { viewModel.repeatChord() },
                  )
                  CircleCenterIconButton(
                      icon = Icons.Rounded.MusicNote,
                      contentDescription = "Repeat setup melody",
                      tint = ShuuenUi.Muted,
                      onClick = { viewModel.playSetupMelody() },
                  )
                }
              },
          )
    }

    if (inputMethod.component == InputComponent.Piano) {
      BottomActionBar(
          onRepeatChord = { viewModel.repeatChord() },
          onRepeatMelody = { viewModel.playSetupMelody() },
          onFinishSession = { viewModel.finishEarly() },
          modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
      )
    }
  }
}

/**
 * Horizontal inset applied to the non-circle children. The frame itself adds no horizontal padding
 * so the circle can be full-bleed; padded children re-apply this to keep their usual margins.
 */
private val ScreenHorizontalPadding = 20.dp

private val ChordBufferHeight = 56.dp
private val ChordCellWidth = 44.dp
private val ChordCellHeight = 48.dp

/**
 * The chord as a row of note cells (lowest note first), styled like the melodies buffer. An
 * answered cell shows its note's label in the active input vocabulary; in an ordered level the
 * next expected cell is highlighted, while "any" order highlights nothing. The lazy row keeps
 * wide chords scrollable on narrow screens; small ones sit centered within it.
 */
@Composable
private fun ChordBuffer(
    quizState: ChordsQuizState,
    answerOrder: ChordAnswerOrder?,
    inputMode: InputMode,
    musicLabels: MusicLabelSettings,
    modifier: Modifier = Modifier,
) {
  val chord = quizState.currentChord
  val targetIndex =
      when (answerOrder) {
        ChordAnswerOrder.FromBottom -> quizState.answeredNotes.size
        ChordAnswerOrder.FromTop -> chord.size - 1 - quizState.answeredNotes.size
        else -> -1
      }
  LazyRow(
      modifier = modifier.fillMaxWidth().height(ChordBufferHeight),
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    itemsIndexed(chord) { index, note ->
      val answered = index in quizState.answeredNotes
      val label =
          if (answered) {
            inputLabelForPitch(
                pitch = note.pitch,
                mode = inputMode,
                root = quizState.root,
                accidentalType = quizState.accidentalType,
                musicLabels = musicLabels,
            )
          } else "-"
      ChordCell(
          position = index + 1,
          label = label,
          answered = answered,
          target = index == targetIndex,
      )
    }
  }
}

@Composable
private fun ChordCell(
    position: Int,
    label: String,
    answered: Boolean,
    target: Boolean,
) {
  val shape = ShuuenUi.ControlShape
  val borderColor = if (target) ShuuenUi.HairlineStrong else ShuuenUi.Hairline
  val textColor = if (answered || target) ShuuenUi.Text else ShuuenUi.Muted
  Column(
      modifier =
          Modifier.width(ChordCellWidth)
              .height(ChordCellHeight)
              .clip(shape)
              .background(
                  when {
                    target -> ShuuenUi.Ink.copy(alpha = 0.08f)
                    answered -> ShuuenUi.Ink.copy(alpha = 0.04f)
                    else -> Color.Transparent
                  }
              )
              .border(1.dp, borderColor, shape)
              .padding(vertical = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
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
  }
}

@Composable
private fun TrainingStatus(
    questionNumber: Int = 1,
    correct: Int = 0,
    incorrect: Int = 0,
    questionsAmount: Int? = null,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
      Text(
          "$questionNumber/${questionsAmount ?: "∞"}",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
      )

      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ScoreCount("$correct", ShuuenUi.Correct)
        Text("|", color = ShuuenUi.Dim, style = MaterialTheme.typography.titleMedium)
        ScoreCount("$incorrect", ShuuenUi.Incorrect)
      }
    }

    LinearTrainingProgress(
        progress = (questionNumber.toFloat() - 1) / (questionsAmount ?: questionNumber),
    )
  }
}

@Composable
private fun ScoreCount(
    value: String,
    tint: Color,
) {
  Text(value, color = tint, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun BottomActionBar(
    onRepeatChord: () -> Unit,
    onRepeatMelody: () -> Unit,
    onFinishSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BottomRepeatButton(Modifier.weight(1.8f), onClick = onRepeatChord)
    BottomIconButton(
        icon = Icons.Rounded.MusicNote,
        modifier = Modifier.width(80.dp),
      onClick = onRepeatMelody
    )
    Spacer(Modifier.weight(0.34f))
    BottomIconButton(
        icon = Icons.Rounded.Flag,
        modifier = Modifier.width(64.dp),
      onClick = onFinishSession
    )
  }
}

@Composable
private fun BottomRepeatButton(modifier: Modifier = Modifier, onClick: () ->Unit) {
  SoftControl(modifier = modifier.height(60.dp), onClick = onClick) {
    Icon(
        imageVector = Icons.Rounded.Replay,
        contentDescription = null,
        tint = ShuuenUi.Text,
        modifier = Modifier.size(24.dp),
    )
    Text(
        text = "Repeat",
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun BottomIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
  SoftControl(modifier = modifier.height(60.dp), onClick = onClick) {
    Icon(icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(24.dp))
  }
}
