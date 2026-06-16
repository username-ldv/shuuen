package ldv.shuuen.ui.screens.training.single.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.common.ResponseState
import ldv.shuuen.domain.audio.music.Pitch
import ldv.shuuen.ui.common.LinearTrainingProgress
import ldv.shuuen.ui.common.ShuuenTopAppBar
import ldv.shuuen.ui.common.ShuuenTopAppBarType
import ldv.shuuen.ui.common.ShuuenUi
import ldv.shuuen.ui.common.SoftControl
import ldv.shuuen.ui.common.StaticScreenFrame
import ldv.shuuen.ui.common.music.inputs.PianoKeyboard
import ldv.shuuen.ui.common.music.inputs.PianoKeyboardDefaults
import ldv.shuuen.ui.common.music.inputs.rememberPianoKeyboardState

@Composable
fun SinglesPlayScreen(
    onNavigateBack: () -> Unit,
    onLevelEnd: () -> Unit,
    viewModel: SinglesPlayScreenViewModel,
) {
  val screenState by viewModel.state.collectAsStateWithLifecycle()
  var useCircleInput by rememberSaveable { mutableStateOf(false) }
  val title =
      when (val level = screenState.levelData) {
        is ResponseState.Loading -> "Loading..."
        is ResponseState.Error -> "Error"
        is ResponseState.Success -> level.result.name
      }

  LaunchedEffect(screenState.phase) {
    when (screenState.phase) {
      is QuizPhase.Complete -> onLevelEnd()
      // for now
      else -> Unit
    }
  }

  StaticScreenFrame(
      scrollable = false,
      topBar = {
        ShuuenTopAppBar(
            title = title,
            onBack = onNavigateBack,
            trailingIcon = Icons.Rounded.Tune,
            onTrailingClick = { useCircleInput = !useCircleInput },
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
      )
    }

    Spacer(Modifier.weight(1f))

    val keyboardState = rememberPianoKeyboardState()

    LaunchedEffect(keyboardState) {
      viewModel.setupMelodyFlashes.collect { req ->
        keyboardState.flash(
            req.index,
            req.color,
            holdMillis = 520,
            attackMillis = 80,
            releaseMillis = 300,
        )
      }
    }

    // pressedKeyColors stays at its neutral default (plain touch feedback); all color comes from
    // flashes.
    PianoKeyboard(
        modifier = Modifier.fillMaxWidth().aspectRatio(PianoKeyboardDefaults.aspectRatio(12)),
        keyCount = 12,
        state = keyboardState,
        onKeyPressedChange = { offset, pressed ->
          if (screenState.phase != QuizPhase.AwaitingAnswer) return@PianoKeyboard
          if (!pressed) {
            val pitch = Pitch.fromOrdinal(offset)
            viewModel.userGuessed(pitch)?.let { correct ->
              val color = if (correct) AnswerColors.Correct.color else AnswerColors.Incorrect.color
              keyboardState.flash(offset, color)
            }
          }
        },
    )

    Spacer(Modifier.weight(0.34f))

    BottomActionBar(
        onRepeatNote = { viewModel.repeatNote() },
        onRepeatMelody = { viewModel.playSetupMelody() },
    )
  }
}

@Composable
private fun TrainingStatus(
    questionNumber: Int = 1,
    correct: Int = 0,
    incorrect: Int = 0,
    questionsAmount: Int? = null,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
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
private fun BottomActionBar(onRepeatNote: () -> Unit, onRepeatMelody: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BottomRepeatButton(Modifier.weight(1.8f).clickable { onRepeatNote() })
    BottomIconButton(
        icon = Icons.Rounded.MusicNote,
        modifier = Modifier.width(80.dp).clickable { onRepeatMelody() },
    )
    Spacer(Modifier.weight(0.34f))
    BottomIconButton(
        icon = Icons.Rounded.Flag,
        modifier = Modifier.width(64.dp),
    )
  }
}

@Composable
private fun BottomRepeatButton(modifier: Modifier = Modifier) {
  SoftControl(modifier = modifier.height(60.dp)) {
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
) {
  SoftControl(modifier = modifier.height(60.dp)) {
    Icon(icon, contentDescription = null, tint = ShuuenUi.Muted, modifier = Modifier.size(24.dp))
  }
}
