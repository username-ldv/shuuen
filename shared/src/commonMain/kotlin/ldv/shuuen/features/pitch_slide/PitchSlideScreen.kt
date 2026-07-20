package ldv.shuuen.features.pitch_slide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import ldv.shuuen.core.ui.components.PillControl
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame

@Composable
fun PitchSlideScreen(
  viewModel: PitchSlideViewModel,
  onNavigateBack: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  StaticScreenFrame(
    scrollable = false,
    topBar = {
      ShuuenTopAppBar(
        title = "PITCH SLIDE",
        subtitle = "Hear the target, then slide the wave to recreate it.",
        onBack = onNavigateBack,
        type = ShuuenTopAppBarType.Labeled,
      )
    },
  ) {
    state.errorMessage?.let { message ->
      Text(text = message, color = ShuuenUi.Incorrect, style = MaterialTheme.typography.bodyMedium)
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            viewModel.slideTo(yToHz(down.position.y, size.height))
            drag(down.id) { change ->
              viewModel.slideTo(yToHz(change.position.y, size.height))
              change.consume()
            }
            viewModel.slideReleased()
          }
        },
    ) {
      PitchWave(
        hz = state.currentHz,
        targetHz = state.targetHz.takeIf { state.revealed },
        sounding = state.isUserToneSounding || state.isTargetPlaying,
        modifier = Modifier.fillMaxSize(),
      )

      Text(
        text = "ROUND ${state.round}",
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.labelLarge.copy(
          letterSpacing = ShuuenUi.labelSpacing,
          fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier.align(Alignment.TopStart).padding(top = 6.dp),
      )

      state.score?.let { score ->
        Column(
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp),
          horizontalAlignment = Alignment.End,
        ) {
          Text(
            text = score.format(2),
            color = ShuuenUi.Text,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
          )
          state.errorCents?.let { cents ->
            Text(
              text = "Off by ${abs(cents).format(1)}¢ ${if (cents >= 0) "sharp" else "flat"}",
              color = ShuuenUi.Muted,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.End,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Bottom,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "TARGET",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = ShuuenUi.labelSpacing,
            fontWeight = FontWeight.SemiBold,
          ),
        )
        Text(
          text = if (state.revealed) "${state.targetHz.format(2)} Hz" else "· · ·",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.headlineLarge,
        )
        Text(
          text = "${state.currentHz.format(2)} Hz",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PillControl(
          text = "HEAR TARGET",
          selected = state.isTargetPlaying,
          leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
          onClick = viewModel::playTarget,
        )
        Box(
          modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(ShuuenUi.Inverse)
            .clickable { if (state.revealed) viewModel.nextRound() else viewModel.check() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (state.revealed) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check,
            contentDescription = if (state.revealed) "Next round" else "Check answer",
            tint = ShuuenUi.OnInverse,
            modifier = Modifier.size(26.dp),
          )
        }
      }
    }
  }
}

private val StrandColors =
  listOf(Color(0xFF67E8F9), Color(0xFF86EFAC), Color(0xFFA78BFA), Color(0xFF60A5FA))

/**
 * The dialed-style vertical wave ribbon: intertwined strands whose spatial density follows the
 * current frequency (higher pitch = tighter wiggles) while their movement runs on wall-clock
 * time. A white handle marks the tone's position on the log-frequency axis (top = high); after
 * the reveal a green line marks where the target actually was.
 */
@Composable
private fun PitchWave(
  hz: Double,
  targetHz: Double?,
  sounding: Boolean,
  modifier: Modifier = Modifier,
) {
  val time by produceState(0f) {
    var start = 0L
    while (true) {
      withFrameNanos { nanos ->
        if (start == 0L) start = nanos
        value = (nanos - start) / 1e9f
      }
    }
  }

  val handleLineColor = ShuuenUi.Ink.copy(alpha = 0.25f)
  val handleColor = ShuuenUi.Ink
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val pif = PI.toFloat()
    val logSpan = ln(PitchSlideMaxHz / PitchSlideMinHz)
    val logPos = (ln(hz / PitchSlideMinHz) / logSpan).toFloat().coerceIn(0f, 1f)
    val cycles = 2.5f + logPos * 9.5f
    val maxAmp = min(w * 0.18f, 120.dp.toPx())
    val alphaBase = if (sounding) 0.8f else 0.45f
    val steps = 96

    StrandColors.forEachIndexed { s, color ->
      val phase = s * pif / 2f
      val speed = 1.1f + s * 0.3f
      val strandCycles = cycles * (1f + (s - 1.5f) * 0.05f)
      for (echo in -1..1) {
        val path = Path()
        for (i in 0..steps) {
          val yn = i / steps.toFloat()
          val envelope = sin(pif * yn).coerceAtLeast(0f).pow(0.8f)
          val breathe = 0.62f + 0.38f * sin(time * 0.5f + s * 1.7f + yn * 2.4f)
          val amp = maxAmp * envelope * breathe * (1f + echo * 0.13f)
          val x = cx + amp * sin(2f * pif * strandCycles * yn + phase + time * speed)
          val y = yn * h
          if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
          path = path,
          color = color,
          alpha = if (echo == 0) alphaBase else alphaBase * 0.3f,
          style = Stroke(
            width = if (echo == 0) 2.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
          ),
        )
      }
    }

    val handleY = (1f - logPos) * h
    drawLine(
      color = handleLineColor,
      start = Offset(cx - 46.dp.toPx(), handleY),
      end = Offset(cx + 46.dp.toPx(), handleY),
      strokeWidth = 1.dp.toPx(),
    )
    drawCircle(color = handleColor, radius = 5.dp.toPx(), center = Offset(cx, handleY))

    if (targetHz != null) {
      val targetPos = (ln(targetHz / PitchSlideMinHz) / logSpan).toFloat().coerceIn(0f, 1f)
      val targetY = (1f - targetPos) * h
      drawLine(
        color = ShuuenUi.Correct.copy(alpha = 0.7f),
        start = Offset(cx - 60.dp.toPx(), targetY),
        end = Offset(cx + 60.dp.toPx(), targetY),
        strokeWidth = 1.5.dp.toPx(),
      )
    }
  }
}

/** Screen y (top = [PitchSlideMaxHz]) to frequency, log-interpolated like the visual axis. */
private fun yToHz(y: Float, heightPx: Int): Double {
  val fraction = (y / heightPx).coerceIn(0f, 1f).toDouble()
  return exp(ln(PitchSlideMaxHz) + (ln(PitchSlideMinHz) - ln(PitchSlideMaxHz)) * fraction)
}

/** Fixed-decimals formatting; common code has no String.format. */
private fun Double.format(decimals: Int): String {
  val factor = 10.0.pow(decimals)
  val scaled = round(abs(this) * factor).toLong()
  val divisor = factor.toLong()
  val sign = if (this < 0) "-" else ""
  val fraction = (scaled % divisor).toString().padStart(decimals, '0')
  return "$sign${scaled / divisor}.$fraction"
}
