package ldv.shuuen.features.training.single.play

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.InputComponent
import ldv.shuuen.core.ui.components.LinearTrainingProgress
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
import ldv.shuuen.features.training.common.components.circleItemNames
import ldv.shuuen.features.training.common.components.circleTopItem
import ldv.shuuen.features.training.common.components.pitchToItemIndex

@Composable
fun SinglesPlayScreen(
    onNavigateBack: () -> Unit,
    onLevelEnd: (sessionId: String) -> Unit,
    viewModel: SinglesPlayScreenViewModel,
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
      is QuizPhase.Complete -> phase.sessionId?.let(onLevelEnd) ?: onNavigateBack()
      else -> Unit
    }
  }

  StaticScreenFrame(
      scrollable = false,
      // The circle input is full-bleed (it must reach the screen edges to stay tappable there), so
      // the frame adds no horizontal padding; each child below applies its own where needed.
      horizontalPadding = 0.dp,
      topBar = {
        ShuuenTopAppBar(
            title = title,
            onBack = onNavigateBack,
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
    }

    val keyboardState = rememberPianoKeyboardState()
    val circleState = rememberFifthsCircleState()

    // The setup-melody flow emits absolute pitches; the index meaning depends on the active input
    // method, and the relative mapping needs the current root. Track both without restarting the
    // collector each time they change.
    val currentInputMethod by rememberUpdatedState(inputMethod)
    val currentRoot by rememberUpdatedState(screenState.quizState?.root)

    LaunchedEffect(Unit) {
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

    // Both components reduce a tapped item to a guess via userGuessed(index, mode); the flash uses
    // the tapped index directly, so no extra mapping is needed for feedback.
    fun handleGuess(index: Int, flashOn: (Int, Color) -> Unit) {
      if (screenState.phase != QuizPhase.AwaitingAnswer) return
      viewModel.userGuessed(index, inputMethod.mode)?.let { correct ->
        val color = if (correct) AnswerColors.Correct.color else AnswerColors.Incorrect.color
        flashOn(index, color)
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
          )
    }

    BottomActionBar(
        onRepeatNote = { viewModel.repeatNote() },
        onRepeatMelody = { viewModel.playSetupMelody() },
        onFinishSession = { viewModel.finishEarly() },
        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
    )
  }
}

/**
 * Horizontal inset applied to the non-circle children. The frame itself adds no horizontal padding
 * so the circle can be full-bleed; padded children re-apply this to keep their usual margins.
 */
private val ScreenHorizontalPadding = 20.dp

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
    onRepeatNote: () -> Unit,
    onRepeatMelody: () -> Unit,
    onFinishSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BottomRepeatButton(Modifier.weight(1.8f), onClick = onRepeatNote)
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
