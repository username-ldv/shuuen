package ldv.shuuen.features.free_play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.music.chromaticSpellingByOrdinal
import ldv.shuuen.core.music.customLabel
import ldv.shuuen.core.music.effectiveDegreeNames
import ldv.shuuen.core.settings.MusicLabelSettings
import ldv.shuuen.core.ui.components.MidiKeyboardBadge
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.music.inputs.FifthsCircle
import ldv.shuuen.core.ui.components.music.inputs.FifthsCircleIndication
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyIndication
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboard
import ldv.shuuen.core.ui.components.music.inputs.PianoKeyboardDefaults

@Composable
fun FreePlayScreen(
  viewModel: FreePlayViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.state.collectAsState()
  val musicLabels by viewModel.musicLabels.collectAsState()

  DisposableEffect(viewModel) {
    onDispose {
      viewModel.onAction(FreePlayAction.StopAll)
    }
  }

  StaticScreenFrame(
    maxWidth = 720.dp,
    topBar = {
      ShuuenTopAppBar(
        title = "FREE PLAY",
        onBack = onNavigateBack,
        statusContent = { MidiKeyboardBadge() },
      )
    },
  ) {
    Text(
      text = state.tonic.toString(),
      color = ShuuenUi.Text,
      style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = ShuuenUi.titlesSpacing),
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    if (state.initializingAudio) {
      Text(
        text = "Preparing audio...",
        style = MaterialTheme.typography.bodySmall,
        color = ShuuenUi.Muted,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }

    state.errorMessage?.let { message ->
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val circleWidth = maxWidth.coerceAtMost(460.dp)
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        FifthsCircle(
          modifier = Modifier
            .width(circleWidth)
            .aspectRatio(1f),
          itemNames = circleLabels(state, musicLabels),
          onItemPressedChange = { index, pressed ->
            if (pressed) viewModel.onAction(FreePlayAction.ToggleDrone(index))
          },
          programmaticIndications = state.activeFifthsItems.map {
            FifthsCircleIndication(it, durationMillis = null)
          },
          onCenterClick = { viewModel.onAction(FreePlayAction.StopAll) },
        )
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val keyboardWidth = maxWidth.coerceAtMost(720.dp)
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        PianoKeyboard(
          modifier = Modifier
            .width(keyboardWidth)
            .aspectRatio(PianoKeyboardDefaults.aspectRatio(12)),
          enabledKeys = state.enabledKeyboardKeys,
          onKeyPressedChange = { index, pressed ->
            viewModel.onAction(
              if (pressed) {
                FreePlayAction.PressPitch(index)
              } else {
                FreePlayAction.ReleasePitch(index)
              },
            )
          },
          programmaticIndications = state.activeKeyboardKeys.map {
            PianoKeyIndication(it, durationMillis = null)
          },
          pressedKeyColors = PianoKeyboardDefaults.pressedColors(12),
        )
      }
    }
  }
}

private fun circleLabels(
  state: FreePlayState,
  musicLabels: MusicLabelSettings,
): List<String> =
  when (state.displayMode) {
    FreePlayDisplayMode.Degrees -> effectiveDegreeNames(musicLabels.degreeNames)
    FreePlayDisplayMode.Notes -> {
      val byOrdinal =
        chromaticSpellingByOrdinal(
          rootOrdinal = state.tonic.ordinal,
          accidentalType = ScaleAccidentalType.Sharps,
        )
      List(12) { offset ->
        byOrdinal[(state.tonic.ordinal + offset).mod(12)].customLabel(musicLabels.noteNames)
      }
    }
  }
