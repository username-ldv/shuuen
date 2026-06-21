package ldv.shuuen.core.ui.components.music.inputs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.ui.components.music.Palette
import kotlin.math.abs

@Immutable
data class PianoKeyIndication(
  val index: Int,
  /**
   * null = persistent while this object is present in programmaticIndications.
   * non-null = animate for this many milliseconds.
   */
  val durationMillis: Long? = null,
  /**
   * Color to render on the key while this indication is active. Overrides [pressedKeyColors]
   * for the affected key. null = fall back to [pressedKeyColors].
   */
  val color: Color? = null,
)

/**
 * Internal bookkeeping for timed indications: how many overlapping timers are active for a key,
 * plus the color to render while they are. The color is captured here when the timer starts so the
 * flash survives its full [PianoKeyIndication.durationMillis] even after the indication has already
 * left programmaticIndications.
 */
private data class TimedIndication(val count: Int, val color: Color?)

object PianoKeyboardDefaults {
  private val BlackPitchClasses = setOf(1, 3, 6, 8, 10)

  fun isBlackKey(index: Int): Boolean {
    val pitchClass = ((index % 12) + 12) % 12
    return pitchClass in BlackPitchClasses
  }

  fun idleColors(keyCount: Int): List<Color> = List(keyCount) { index ->
    if (isBlackKey(index)) Color.Black else Color(0xFFF2F2F2)
  }

  /** Neutral gray press feedback — visible on both white and black keys. */
  val MonochromePressedColor = Color(0xFFA9A9B2)

  fun pressedColors(
    keyCount: Int,
    color: Color = MonochromePressedColor,
  ): List<Color> = List(keyCount) { color }

  /** Opt-in per-degree colors; the app default is monochrome ([pressedColors]). */
  fun colorfulPressedColors(
    keyCount: Int, tonic: Pitch = Pitch.C
  ): List<Color> {
    return List(keyCount) { keyNumber ->
      // no idea how I wrote this.
      val tonicOffset = keyNumber - tonic.ordinal
      val colorIndex = if (tonicOffset >= 0) tonicOffset else tonicOffset + 12
      Palette.entries[colorIndex].color
    }
  }

  fun disabledColors(keyCount: Int): List<Color> = List(keyCount) { index ->
    if (isBlackKey(index)) Color(0xFF3B3B3B) else Color(0xFFBDBDBD)
  }

  fun aspectRatio(keyCount: Int): Float {
    val whiteCount = (0 until keyCount).count { !isBlackKey(it) }
    return whiteCount / 4.2f
  }
}

/**
 * A single transient "flash" on a key: a colored highlight that animates in quickly, holds briefly,
 * then fades out. Each flash carries its own identity and [Animatable] progress, so overlapping
 * flashes — even on the same key — are fully independent.
 */
@Stable
class KeyFlash internal constructor(
  val id: Long,
  val index: Int,
  val color: Color,
) {
  val progress = Animatable(0f)
}

/**
 * Hoisted state for [PianoKeyboard]. Owns transient tap-feedback flashes so callers don't manage
 * indication ids or removal timers themselves. Obtain one via [rememberPianoKeyboardState] and call
 * [flash] on key release. Independent of touch presses and of any persistent [PianoKeyIndication]s.
 */
@Stable
class PianoKeyboardState(private val scope: CoroutineScope) {
  internal val flashes = mutableStateListOf<KeyFlash>()
  private var nextId = 0L

  /**
   * Fire a one-shot colored flash on [index]: a fast attack, a brief hold, then a smooth fade-out.
   * Safe to call rapidly and repeatedly; each call is animated on its own coroutine and cleaned up.
   */
  fun flash(
    index: Int,
    color: Color,
    holdMillis: Long = 200L,
    attackMillis: Int = 90,
    releaseMillis: Int = 300,
  ) {
    val flash = KeyFlash(nextId++, index, color)
    flashes.add(flash)
    scope.launch {
      try {
        flash.progress.animateTo(1f, tween(attackMillis, easing = FastOutSlowInEasing))
        delay(holdMillis)
        flash.progress.animateTo(0f, tween(releaseMillis, easing = FastOutSlowInEasing))
      } finally {
        flashes.remove(flash)
      }
    }
  }
}

@Composable
fun rememberPianoKeyboardState(): PianoKeyboardState {
  val scope = rememberCoroutineScope()
  return remember { PianoKeyboardState(scope) }
}

@Composable
fun PianoKeyboard(
  modifier: Modifier = Modifier,

  keyCount: Int = 12,

  idleKeyColors: List<Color> = PianoKeyboardDefaults.idleColors(keyCount),
  pressedKeyColors: List<Color> = PianoKeyboardDefaults.pressedColors(keyCount),
  disabledKeyColors: List<Color> = PianoKeyboardDefaults.disabledColors(keyCount),
  enabledKeys: List<Boolean> = List(keyCount) { true },

  /**
   * External, declarative visual indications.
   *
   * PianoKeyIndication(index = 0, durationMillis = null) stays active until removed.
   * PianoKeyIndication(index = 0, durationMillis = 450) pulses for 450ms.
   */
  programmaticIndications: List<PianoKeyIndication> = emptyList(),

  /**
   * Optional hoisted state for transient tap-feedback flashes. See [rememberPianoKeyboardState].
   */
  state: PianoKeyboardState? = null,

  onKeyClick: (index: Int) -> Unit = {},
  onKeyPressedChange: (index: Int, pressed: Boolean) -> Unit = { _, _ -> },

  borderColor: Color = Color(0xFF202124),
  separatorColor: Color = Color(0xFF202124),
  disabledOverlayColor: Color = Color.Black.copy(alpha = 0.16f),

  borderWidth: Dp = 4.dp,
  separatorWidth: Dp = 2.dp,

  blackKeyWidthFraction: Float = 0.52f,
  blackKeyHeightFraction: Float = 0.62f,

  whiteKeyCornerRadius: Dp = 0.dp,
  blackKeyCornerRadius: Dp = 2.dp,
) {
  require(keyCount > 0) {
    "keyCount must be greater than 0."
  }
  require(idleKeyColors.size == keyCount) {
    "idleKeyColors must have size keyCount."
  }
  require(pressedKeyColors.size == keyCount) {
    "pressedKeyColors must have size keyCount."
  }
  require(disabledKeyColors.size == keyCount) {
    "disabledKeyColors must have size keyCount."
  }
  require(enabledKeys.size == keyCount) {
    "enabledKeys must have size keyCount."
  }
  require(blackKeyWidthFraction in 0.1f..0.95f) {
    "blackKeyWidthFraction should be between 0.1 and 0.95."
  }
  require(blackKeyHeightFraction in 0.1f..0.95f) {
    "blackKeyHeightFraction should be between 0.1 and 0.95."
  }

  val latestOnKeyClick by rememberUpdatedState(onKeyClick)
  val latestOnKeyPressedChange by rememberUpdatedState(onKeyPressedChange)

  val touchPointers = remember { mutableStateMapOf<PointerId, Int>() }
  val timedProgrammatic = remember { mutableStateMapOf<Int, TimedIndication>() }

  val timedIndications = remember(programmaticIndications) {
    programmaticIndications.filter { it.durationMillis != null }
  }

  LaunchedEffect(timedIndications, keyCount, enabledKeys) {
    timedIndications.forEach { indication ->
      val index = indication.index
      val duration = indication.durationMillis ?: return@forEach

      if (index !in 0 until keyCount) return@forEach
      if (!enabledKeys[index]) return@forEach

      launch {
        val existing = timedProgrammatic[index]
        timedProgrammatic[index] = TimedIndication(
          count = (existing?.count ?: 0) + 1,
          color = indication.color ?: existing?.color,
        )

        try {
          delay(duration.coerceAtLeast(1L))
        } finally {
          val current = timedProgrammatic[index]
          val next = (current?.count ?: 1) - 1

          if (next <= 0) {
            timedProgrammatic.remove(index)
          } else {
            timedProgrammatic[index] = current?.copy(count = next) ?: TimedIndication(next, null)
          }
        }
      }
    }
  }

  val persistentProgrammaticKeys =
    programmaticIndications.asSequence().filter { it.durationMillis == null }.map { it.index }
      .filter { it in 0 until keyCount && enabledKeys[it] }.toSet()

  val persistentIndicationColors =
    programmaticIndications.asSequence()
      .filter { it.durationMillis == null && it.color != null }
      .filter { it.index in 0 until keyCount && enabledKeys[it.index] }
      .associate { it.index to it.color!! }

  val activeKeys =
    (touchPointers.values.toSet() + persistentProgrammaticKeys + timedProgrammatic.keys).filter { it in 0 until keyCount && enabledKeys[it] }
      .toSet()

  // Effective press-target color for a key: an active indication's color wins over pressedKeyColors.
  fun targetPressColor(index: Int): Color =
    persistentIndicationColors[index] ?: timedProgrammatic[index]?.color ?: pressedKeyColors[index]

  val pressProgress = List(keyCount) { index ->
    animateFloatAsState(
      targetValue = if (index in activeKeys) 1f else 0f,
      animationSpec = spring(
        dampingRatio = 0.3f,
        stiffness = 300f,
      ),
      label = "piano-key-press-$index",
    ).value
  }

  val infiniteTransition = rememberInfiniteTransition(label = "piano-key-pulse")

  val pulse by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = 850,
        easing = LinearEasing,
      ),
      repeatMode = RepeatMode.Restart,
    ),
    label = "piano-key-pulse-value",
  )

  Canvas(
    modifier = modifier.pointerInput(
      keyCount,
      enabledKeys,
      borderWidth,
      blackKeyWidthFraction,
      blackKeyHeightFraction,
    ) {
      fun currentGeometry(): List<PianoKeyGeometry> = buildPianoKeyGeometry(
        width = size.width.toFloat(),
        height = size.height.toFloat(),
        keyCount = keyCount,
        borderPx = borderWidth.toPx(),
        blackKeyWidthFraction = blackKeyWidthFraction,
        blackKeyHeightFraction = blackKeyHeightFraction,
      )

      fun hitTest(position: Offset): Int? {
        val geometry = currentGeometry()

        val blackHit =
          geometry.asSequence()
            .filter { it.isBlack && it.hitRect.contains(position) }
            .minByOrNull { abs(position.x - it.rect.center.x) }

        if (blackHit != null) {
          return if (enabledKeys[blackHit.index]) {
            blackHit.index
          } else {
            null
          }
        }

        val whiteHit =
          geometry.asSequence().filter { !it.isBlack }.firstOrNull { it.rect.contains(position) }

        return if (whiteHit != null && enabledKeys[whiteHit.index]) {
          whiteHit.index
        } else {
          null
        }
      }

      fun emitReleaseIfNeeded(keyIndex: Int) {
        if (!touchPointers.containsValue(keyIndex)) {
          latestOnKeyPressedChange(keyIndex, false)
        }
      }

      fun setPointerKey(
        pointerId: PointerId,
        newKey: Int?,
      ): Boolean {
        val oldKey = touchPointers[pointerId]

        if (oldKey == newKey) {
          return newKey != null
        }

        if (oldKey != null) {
          touchPointers.remove(pointerId)
          emitReleaseIfNeeded(oldKey)
        }

        if (newKey != null) {
          val wasAlreadyPressed = touchPointers.containsValue(newKey)

          touchPointers[pointerId] = newKey
          latestOnKeyClick(newKey)

          if (!wasAlreadyPressed) {
            latestOnKeyPressedChange(newKey, true)
          }
        }

        return oldKey != null || newKey != null
      }

      fun releasePointer(pointerId: PointerId): Boolean {
        val oldKey = touchPointers.remove(pointerId) ?: return false
        emitReleaseIfNeeded(oldKey)
        return true
      }

      fun releaseAll() {
        val releasedKeys = touchPointers.values.toSet()
        touchPointers.clear()
        releasedKeys.forEach { latestOnKeyPressedChange(it, false) }
      }

      try {
        awaitEachGesture {
          try {
            val firstDown =
              awaitPointerEvent().changes.firstOrNull { it.pressed && !it.previousPressed }
                ?: return@awaitEachGesture

            if (setPointerKey(firstDown.id, hitTest(firstDown.position))) {
              firstDown.consume()
            }

            while (true) {
              val event = awaitPointerEvent()

              event.changes.forEach { change ->
                val consumed = when {
                  change.pressed && !change.previousPressed -> {
                    setPointerKey(
                      pointerId = change.id,
                      newKey = hitTest(change.position),
                    )
                  }

                  change.pressed && change.previousPressed -> {
                    setPointerKey(
                      pointerId = change.id,
                      newKey = hitTest(change.position),
                    )
                  }

                  !change.pressed && change.previousPressed -> {
                    releasePointer(change.id)
                  }

                  else -> false
                }

                if (consumed) {
                  change.consume()
                }
              }

              if (event.changes.none { it.pressed }) {
                break
              }
            }
          } finally {
            releaseAll()
          }
        }
      } finally {
        releaseAll()
      }
    },
  ) {
    val geometry = buildPianoKeyGeometry(
      width = size.width,
      height = size.height,
      keyCount = keyCount,
      borderPx = borderWidth.toPx(),
      blackKeyWidthFraction = blackKeyWidthFraction,
      blackKeyHeightFraction = blackKeyHeightFraction,
    )

    val whiteCorner = CornerRadius(
      whiteKeyCornerRadius.toPx(),
      whiteKeyCornerRadius.toPx(),
    )

    val blackCorner = CornerRadius(
      blackKeyCornerRadius.toPx(),
      blackKeyCornerRadius.toPx(),
    )

    val separatorWidthPx = separatorWidth.toPx()
    val borderWidthPx = borderWidth.toPx()

    // Transient tap-feedback flashes, drawn per-key inside each loop so they layer correctly with
    // the black keys. Reading flashes + each progress here keeps the animation in the draw phase.
    val flashesByIndex = state?.flashes?.groupBy { it.index } ?: emptyMap()

    // White keys first.
    geometry.filter { !it.isBlack }.forEach { key ->
      val index = key.index
      val progress = pressProgress[index]
      val enabled = enabledKeys[index]
      val rect = key.rect
      val targetColor = targetPressColor(index)

      val baseColor = when {
        !enabled -> disabledKeyColors[index]
        else -> lerp(
          idleKeyColors[index],
          targetColor,
          progress,
        )
      }

      drawRoundRect(
        color = baseColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = whiteCorner,
      )

      if (enabled && progress > 0.01f) {
        val activeColor = targetColor
        val pulseAlpha = 0.10f + 0.10f * pulse

        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.22f * progress),
              activeColor.copy(alpha = pulseAlpha * progress),
              Color.Transparent,
            ),
            startY = rect.top,
            endY = rect.bottom,
          ),
          topLeft = rect.topLeft,
          size = rect.size,
          cornerRadius = whiteCorner,
        )

//          drawRoundRect(
//            color = activeColor.copy(alpha = 0.55f * progress),
//            topLeft = rect.topLeft,
//            size = rect.size,
//            cornerRadius = whiteCorner,
//            style = Stroke(width = 2.dp.toPx()),
//          )
      }

      flashesByIndex[index]?.forEach { flash ->
        val p = flash.progress.value
        if (p > 0.001f) {
          drawRoundRect(
            color = flash.color.copy(alpha = 0.85f * p),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = whiteCorner,
          )
        }
      }

      if (!enabled) {
        drawRoundRect(
          color = disabledOverlayColor,
          topLeft = rect.topLeft,
          size = rect.size,
          cornerRadius = whiteCorner,
        )
      }
    }

    // White separators.
    geometry.filter { !it.isBlack }.drop(1).forEach { key ->
      drawLine(
        color = separatorColor,
        start = Offset(key.rect.left, key.rect.top),
        end = Offset(key.rect.left, key.rect.bottom),
        strokeWidth = separatorWidthPx,
      )
    }

    // Black key shadows.
    geometry.filter { it.isBlack }.forEach { key ->
      val rect = key.rect

      drawRoundRect(
        color = Color.Black.copy(alpha = 0.30f),
        topLeft = Offset(
          x = rect.left - 2.dp.toPx(),
          y = rect.top,
        ),
        size = Size(
          width = rect.width + 4.dp.toPx(),
          height = rect.height + 4.dp.toPx(),
        ),
        cornerRadius = blackCorner,
      )
    }

    // Black keys over white keys.
    geometry.filter { it.isBlack }.forEach { key ->
      val index = key.index
      val progress = pressProgress[index]
      val enabled = enabledKeys[index]
      val rect = key.rect
      val targetColor = targetPressColor(index)

      val baseColor = when {
        !enabled -> disabledKeyColors[index]
        else -> lerp(
          idleKeyColors[index],
          targetColor,
          progress,
        )
      }

      drawRoundRect(
        color = baseColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = blackCorner,
      )

      if (enabled && progress > 0.01f) {
        val activeColor = targetColor
        val pulseAlpha = 0.16f + 0.14f * pulse

        drawRoundRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.18f * progress),
              activeColor.copy(alpha = pulseAlpha * progress),
              Color.Transparent,
            ),
            startY = rect.top,
            endY = rect.bottom,
          ),
          topLeft = rect.topLeft,
          size = rect.size,
          cornerRadius = blackCorner,
        )

        drawRoundRect(
          color = activeColor.copy(alpha = 0.75f * progress),
          topLeft = rect.topLeft,
          size = rect.size,
          cornerRadius = blackCorner,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      flashesByIndex[index]?.forEach { flash ->
        val p = flash.progress.value
        if (p > 0.001f) {
          drawRoundRect(
            color = flash.color.copy(alpha = 0.85f * p),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = blackCorner,
          )
        }
      }

      if (!enabled) {
        drawRoundRect(
          color = disabledOverlayColor,
          topLeft = rect.topLeft,
          size = rect.size,
          cornerRadius = blackCorner,
        )
      }

      drawRoundRect(
        color = separatorColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = blackCorner,
        style = Stroke(width = 2.dp.toPx()),
      )
    }

    // Outer border.
    drawRect(
      color = borderColor,
      topLeft = Offset.Zero,
      size = size,
      style = Stroke(width = borderWidthPx),
    )
  }
}

data class PianoKeyGeometry(
  val index: Int,
  val isBlack: Boolean,
  val rect: Rect,
  val hitRect: Rect = rect,
)

fun buildPianoKeyGeometry(
  width: Float,
  height: Float,
  keyCount: Int,
  borderPx: Float,
  blackKeyWidthFraction: Float,
  blackKeyHeightFraction: Float,
): List<PianoKeyGeometry> {
  val safeWidth = width.coerceAtLeast(1f)
  val safeHeight = height.coerceAtLeast(1f)

  val contentLeft = borderPx
  val contentTop = borderPx
  val contentRight = safeWidth - borderPx
  val contentBottom = safeHeight - borderPx

  val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
  val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)

  val whiteCount =
    (0 until keyCount).count { !PianoKeyboardDefaults.isBlackKey(it) }.coerceAtLeast(1)

  val whiteKeyWidth = contentWidth / whiteCount
  val blackKeyWidth = whiteKeyWidth * blackKeyWidthFraction
  val blackKeyHeight = contentHeight * blackKeyHeightFraction

  val keys = mutableListOf<PianoKeyGeometry>()

  for (index in 0 until keyCount) {
    val isBlack = PianoKeyboardDefaults.isBlackKey(index)

    if (!isBlack) {
      val whiteOrdinal = countWhiteKeysBefore(index)

      val left = contentLeft + whiteOrdinal * whiteKeyWidth
      val right = contentLeft + (whiteOrdinal + 1) * whiteKeyWidth

      keys += PianoKeyGeometry(
        index = index,
        isBlack = false,
        rect = Rect(
          left = left,
          top = contentTop,
          right = right,
          bottom = contentBottom,
        ),
      )
    }
  }

  for (index in 0 until keyCount) {
    val isBlack = PianoKeyboardDefaults.isBlackKey(index)

    if (isBlack) {
      val whiteKeysBefore = countWhiteKeysBefore(index)
      val centerX = contentLeft + whiteKeysBefore * whiteKeyWidth

      val left = centerX - blackKeyWidth / 2f
      val right = centerX + blackKeyWidth / 2f
      val hitboxHalfWidth = whiteKeyWidth * 0.5f

      keys += PianoKeyGeometry(
        index = index,
        isBlack = true,
        rect = Rect(
          left = left.coerceAtLeast(contentLeft),
          top = contentTop,
          right = right.coerceAtMost(contentRight),
          bottom = contentTop + blackKeyHeight,
        ),
        hitRect = Rect(
          left = (left - hitboxHalfWidth).coerceAtLeast(contentLeft),
          top = contentTop,
          right = (right + hitboxHalfWidth).coerceAtMost(contentRight),
          bottom = contentTop + blackKeyHeight,
        ),
      )
    }
  }

  return keys
}

private fun countWhiteKeysBefore(index: Int): Int =
  (0 until index).count { !PianoKeyboardDefaults.isBlackKey(it) }

