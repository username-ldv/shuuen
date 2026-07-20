package ldv.shuuen.core.ui.components.music.inputs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.music.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class FifthsCircleIndication(
  val index: Int,
  /**
   * null = persistent while this object is present in programmaticIndications.
   * non-null = pulse for this many milliseconds.
   */
  val durationMillis: Long? = null,
  /**
   * Color to render on the item while this indication is active. Overrides [itemColors] for the
   * affected item. null = fall back to [itemColors].
   */
  val color: Color? = null,
)

/**
 * Internal bookkeeping for timed indications: how many overlapping timers are active for an item,
 * plus the color to render while they are. The color is captured here when the timer starts so the
 * pulse survives its full [FifthsCircleIndication.durationMillis] even after the indication has
 * already left programmaticIndications.
 */
private data class TimedCircleIndication(val count: Int, val color: Color?)

/**
 * A single transient "flash" on a circle item: a colored highlight that animates in quickly, holds
 * briefly, then fades out. Each flash carries its own identity and [Animatable] progress, so
 * overlapping flashes — even on the same item — are fully independent. Mirrors the piano's KeyFlash.
 */
@Stable
class CircleFlash internal constructor(
  val id: Long,
  val index: Int,
  val color: Color,
) {
  val progress = Animatable(0f)
}

/**
 * Hoisted state for [FifthsCircle]. Owns transient tap-feedback flashes so callers don't manage
 * indication ids or removal timers themselves. Obtain one via [rememberFifthsCircleState] and call
 * [flash] on item release. Independent of touch presses and of any persistent
 * [FifthsCircleIndication]s. Mirrors [PianoKeyboardState].
 */
@Stable
class FifthsCircleState(private val scope: CoroutineScope) {
  internal val flashes = mutableStateListOf<CircleFlash>()
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
    val flash = CircleFlash(nextId++, index, color)
    flashes.add(flash)
    scope.launch {
      try {
        flash.progress.animateTo(1f, tween(attackMillis, easing = FastOutSlowInEasing))
        delay(holdMillis.milliseconds)
        flash.progress.animateTo(0f, tween(releaseMillis, easing = FastOutSlowInEasing))
      } finally {
        flashes.remove(flash)
      }
    }
  }
}

@Composable
fun rememberFifthsCircleState(): FifthsCircleState {
  val scope = rememberCoroutineScope()
  return remember { FifthsCircleState(scope) }
}

object FifthsCircleDefalts {
  val Names = listOf(
    "1", "♭2", "2", "♭3", "3", "4", "♯4", "5", "♭6", "6", "♭7", "7"
  )


  /** Monotone active-item color — the app default. Inverts on light themes. */
  @Composable
  fun colors(count: Int): List<Color> {
    val active = if (ShuuenUi.IsDark) Color(0xFFE8E8E8) else Color(0xFF17171A)
    return List(count) { active }
  }

  /** Opt-in per-item colors for a future settings choice. */
  fun colorfulColors(count: Int): List<Color> =
    List(count) { Palette.entries[it % Palette.entries.size].color }

  /**
   * For 12 items this returns:
   * 0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10, 5
   */
  fun circleOfFifthsVisualOrder(
    count: Int,
    fifthStep: Int = 7,
  ): List<Int> {
    if (count <= 0) return emptyList()

    val step = ((fifthStep % count) + count) % count
    if (step == 0 || gcd(step, count) != 1) {
      return List(count) { it }
    }

    return List(count) { slot -> (slot * step) % count }
  }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun FifthsCircle(
  modifier: Modifier = Modifier,

  itemNames: List<String> = FifthsCircleDefalts.Names,
  itemColors: List<Color> = FifthsCircleDefalts.colors(itemNames.size),
  enabledItems: List<Boolean> = List(itemNames.size) { true },

  /**
   * Contains item indices, not label strings.
   * Default lays chromatic itemNames out around the circle of fifths.
   */
  visualOrder: List<Int> = FifthsCircleDefalts.circleOfFifthsVisualOrder(itemNames.size),

  /**
   * When non-null, the whole ring is rotated so this item index ends up at the top slot, animating
   * along the shortest path whenever it changes. null keeps [visualOrder]'s own top item at the top
   * (no rotation). Item indices are unaffected, so taps and indications still address the same items.
   */
  rotateItemToTop: Int? = null,
  rotationAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 550, easing = FastOutSlowInEasing),

  /**
   * External, declarative visual indications.
   *
   * NoteDegreeIndication(index = 0, durationMillis = null) stays active until removed.
   * NoteDegreeIndication(index = 0, durationMillis = 450) pulses for 450ms.
   */
  programmaticIndications: List<FifthsCircleIndication> = emptyList(),

  /**
   * Optional hoisted state for transient tap-feedback flashes. See [rememberFifthsCircleState].
   */
  state: FifthsCircleState? = null,

  onItemClick: (index: Int) -> Unit = {},
  onItemPressedChange: (index: Int, pressed: Boolean) -> Unit = { _, _ -> },

  backgroundColor: Color = Color.Transparent,
  ringColor: Color = ShuuenUi.Ink.copy(alpha = 0.18f),
  inactiveDotColor: Color = if (ShuuenUi.IsDark) Color(0xFF7A7A80) else Color(0xFF85858B),
  disabledDotColor: Color = if (ShuuenUi.IsDark) Color(0xFF4B4B50) else Color(0xFFB4B4B9),
  inactiveLabelColor: Color = if (ShuuenUi.IsDark) Color(0xFF9B9BA1) else Color(0xFF64646A),
  activeLabelColor: Color = ShuuenUi.Ink,
  disabledLabelColor: Color = if (ShuuenUi.IsDark) Color(0xFF5D5D63) else Color(0xFFA2A2A8),

  ringStrokeWidth: Dp = 1.dp,
  outerPadding: Dp = 30.dp,
  /**
   * When set, positions the ring so each resting dot's outer edge is this far
   * from the component edge. Null keeps the legacy ring-center outerPadding.
   */
  dotEdgePadding: Dp? = null,
  itemTouchRadius: Dp = 36.dp,
  inactiveDotRadius: Dp = 7.dp,
  activeDotRadius: Dp = 10.dp,
  activeHaloRadius: Dp = 28.dp,
  labelInset: Dp = 24.dp,
  labelRingGap: Dp = 2.dp,
  accidentalTuck: Dp = 5.dp,
  labelStyle: TextStyle = TextStyle(
    fontSize = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp
  ),

  centerButtonSize: Dp = 104.dp,
  onCenterClick: (() -> Unit)? = null,
  centerContent: (@Composable BoxScope.() -> Unit)? = null,
) {
  require(itemNames.isNotEmpty()) { "itemNames must not be empty." }
  require(itemColors.size == itemNames.size) {
    "itemColors must have the same size as itemNames."
  }
  require(enabledItems.size == itemNames.size) {
    "enabledItems must have the same size as itemNames."
  }
  require(visualOrder.size == itemNames.size && visualOrder.toSet() == itemNames.indices.toSet()) {
    "visualOrder must contain each item index exactly once."
  }

  val itemCount = itemNames.size
  val textMeasurer = rememberTextMeasurer()

  // Whole-ring rotation (radians) used to bring [rotateItemToTop] to the top slot. Animated along
  // the shortest path so a changing root spins the closest way round instead of unwinding fully.
  val rotation = remember { Animatable(0f) }
  val targetRotation = run {
    val slot = rotateItemToTop?.let { visualOrder.indexOf(it) } ?: -1
    if (slot >= 0) -(2f * PI.toFloat() * slot / itemCount) else 0f
  }
  LaunchedEffect(targetRotation) {
    val twoPi = 2f * PI.toFloat()
    val current = rotation.value
    var delta = (targetRotation - current) % twoPi
    if (delta > PI) delta -= twoPi
    if (delta < -PI) delta += twoPi
    rotation.animateTo(current + delta, animationSpec = rotationAnimationSpec)
  }

  val latestOnItemClick by rememberUpdatedState(onItemClick)
  val latestOnItemPressedChange by rememberUpdatedState(onItemPressedChange)

  val touchPointers = remember { mutableStateMapOf<PointerId, Int>() }
  val timedProgrammatic = remember { mutableStateMapOf<Int, TimedCircleIndication>() }

  val timedIndications = remember(programmaticIndications) {
    programmaticIndications.filter { it.durationMillis != null }
  }

  LaunchedEffect(timedIndications) {
    timedIndications.forEach { indication ->
      val index = indication.index
      val duration = indication.durationMillis ?: return@forEach

      if (index !in 0 until itemCount) return@forEach
      if (!enabledItems[index]) return@forEach

      launch {
        val existing = timedProgrammatic[index]
        timedProgrammatic[index] = TimedCircleIndication(
          count = (existing?.count ?: 0) + 1,
          color = indication.color ?: existing?.color,
        )

        try {
          delay(duration.coerceAtLeast(1L).milliseconds)
        } finally {
          val current = timedProgrammatic[index]
          val next = (current?.count ?: 1) - 1
          if (next <= 0) {
            timedProgrammatic.remove(index)
          } else {
            timedProgrammatic[index] = current?.copy(count = next) ?: TimedCircleIndication(next, null)
          }
        }
      }
    }
  }

  val persistentProgrammaticItems =
    programmaticIndications.asSequence().filter { it.durationMillis == null }.map { it.index }
      .filter { it in 0 until itemCount && enabledItems[it] }.toSet()

  val persistentIndicationColors =
    programmaticIndications.asSequence()
      .filter { it.durationMillis == null && it.color != null }
      .filter { it.index in 0 until itemCount && enabledItems[it.index] }
      .associate { it.index to it.color!! }

  val activeItems =
    (touchPointers.values.toSet() + persistentProgrammaticItems + timedProgrammatic.keys).filter { it in 0 until itemCount && enabledItems[it] }
      .toSet()

  // Effective active color for an item: an active indication's color wins over itemColors.
  fun effectiveColor(index: Int): Color =
    persistentIndicationColors[index] ?: timedProgrammatic[index]?.color ?: itemColors[index]

  val pressProgress = List(itemCount) { index ->
    animateFloatAsState(
      targetValue = if (index in activeItems) 1f else 0f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
      ),
      label = "note-degree-press-$index",
    ).value
  }

  val infiniteTransition = rememberInfiniteTransition(label = "note-degree-pulse")
  val pulse by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "note-degree-pulse-value",
  )

  // No internal aspectRatio: the circle is drawn centered and sized to the min dimension, so the
  // caller controls the shape. Giving a non-square (e.g. taller) area makes the surrounding region
  // part of the touch surface — items near the edge stay hittable even where their touch radius
  // spills past the ring. Callers that want a square should pass Modifier.aspectRatio(1f).
  Box(
    modifier = modifier
      .background(backgroundColor),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(
          itemCount,
          enabledItems,
          visualOrder,
          outerPadding,
          dotEdgePadding,
          inactiveDotRadius,
          itemTouchRadius,
        ) {
          fun itemPositionForSlot(slot: Int): Offset {
            val minSide = min(size.width, size.height).toFloat()
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = fifthsCircleRingRadiusPx(
              minSidePx = minSide,
              outerPaddingPx = outerPadding.toPx(),
              dotRadiusPx = inactiveDotRadius.toPx(),
              dotEdgePaddingPx = dotEdgePadding?.toPx(),
            )
            // Read the live rotation so taps land on items where they currently are mid-spin.
            return pointOnCircle(center, radius, slot, itemCount, rotation.value)
          }

          fun hitTest(position: Offset): Int? {
            val hitRadiusPx = itemTouchRadius.toPx()
            val hitRadiusSquared = hitRadiusPx * hitRadiusPx

            var bestIndex: Int? = null
            var bestDistanceSquared = Float.MAX_VALUE

            visualOrder.forEachIndexed { slot, itemIndex ->
              if (!enabledItems[itemIndex]) return@forEachIndexed

              val itemCenter = itemPositionForSlot(slot)
              val dx = position.x - itemCenter.x
              val dy = position.y - itemCenter.y
              val distanceSquared = dx * dx + dy * dy

              if (distanceSquared <= hitRadiusSquared && distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                bestIndex = itemIndex
              }
            }

            return bestIndex
          }

          fun emitReleaseIfNeeded(itemIndex: Int) {
            if (!touchPointers.containsValue(itemIndex)) {
              latestOnItemPressedChange(itemIndex, false)
            }
          }

          fun setPointerItem(pointerId: PointerId, newItem: Int?): Boolean {
            val oldItem = touchPointers[pointerId]
            if (oldItem == newItem) return newItem != null

            if (oldItem != null) {
              touchPointers.remove(pointerId)
              emitReleaseIfNeeded(oldItem)
            }

            if (newItem != null) {
              val wasAlreadyPressed = touchPointers.containsValue(newItem)
              touchPointers[pointerId] = newItem

              latestOnItemClick(newItem)

              if (!wasAlreadyPressed) {
                latestOnItemPressedChange(newItem, true)
              }
            }

            return oldItem != null || newItem != null
          }

          fun releasePointer(pointerId: PointerId): Boolean {
            val oldItem = touchPointers.remove(pointerId) ?: return false
            emitReleaseIfNeeded(oldItem)
            return true
          }

          fun releaseAll() {
            val releasedItems = touchPointers.values.toSet()
            touchPointers.clear()
            releasedItems.forEach { latestOnItemPressedChange(it, false) }
          }

          try {
            awaitEachGesture {
              try {
                val firstDown = awaitPointerEvent().changes.firstOrNull {
                  it.pressed && !it.previousPressed
                } ?: return@awaitEachGesture

                if (setPointerItem(firstDown.id, hitTest(firstDown.position))) {
                  firstDown.consume()
                }

                while (true) {
                  val event = awaitPointerEvent()

                  event.changes.forEach { change ->
                    val consumed = when {
                      change.pressed && !change.previousPressed -> {
                        setPointerItem(
                          pointerId = change.id,
                          newItem = hitTest(change.position),
                        )
                      }

                      change.pressed && change.previousPressed -> {
                        setPointerItem(
                          pointerId = change.id,
                          newItem = hitTest(change.position),
                        )
                      }

                      !change.pressed && change.previousPressed -> {
                        releasePointer(change.id)
                      }

                      else -> false
                    }

                    if (consumed) change.consume()
                  }

                  if (event.changes.none { it.pressed }) break
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
      val minSide = size.minDimension
      val center = this.center

      val ringStrokePx = ringStrokeWidth.toPx()
      val ringRadius = fifthsCircleRingRadiusPx(
        minSidePx = minSide,
        outerPaddingPx = outerPadding.toPx(),
        dotRadiusPx = inactiveDotRadius.toPx(),
        dotEdgePaddingPx = dotEdgePadding?.toPx(),
      )
      val innerMaskRadius = max(0f, ringRadius - ringStrokePx / 2f)

      val inactiveDotRadiusPx = inactiveDotRadius.toPx()
      val activeDotRadiusPx = activeDotRadius.toPx()
      val haloRadiusPx = activeHaloRadius.toPx()
      val labelInsetPx = labelInset.toPx()
      val labelRingGapPx = labelRingGap.toPx()
      val accidentalTuckPx = accidentalTuck.toPx()
      val cardinalLabelTuckPx =
        max(inactiveDotRadiusPx, activeDotRadiusPx) * CardinalLabelTuckFraction

      // Outer colored halos. The inner circle is masked afterward,
      // which creates the nice outward semicircle effect.
      visualOrder.forEachIndexed { slot, itemIndex ->
        val progress = pressProgress[itemIndex]
        if (progress <= 0.01f || !enabledItems[itemIndex]) return@forEachIndexed

        val itemCenter = pointOnCircle(center, ringRadius, slot, itemCount, rotation.value)
        val color = effectiveColor(itemIndex)

        val breathing = 0.94f + 0.12f * pulse
        val haloRadius = lerpFloat(activeDotRadiusPx, haloRadiusPx * breathing, progress)

        drawCircle(
          color = color.copy(alpha = 0.34f * progress),
          radius = haloRadius,
          center = itemCenter,
        )

        drawCircle(
          color = color.copy(alpha = 0.18f * progress * (1f - pulse)),
          radius = haloRadiusPx * (0.85f + 0.35f * pulse),
          center = itemCenter,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      // Mask inner half of halos.
      drawCircle(
        color = backgroundColor,
        radius = innerMaskRadius,
        center = center,
      )

      // Main ring.
      drawCircle(
        color = ringColor,
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringStrokePx),
      )

      // Dots and labels.
      visualOrder.forEachIndexed { slot, itemIndex ->
        val enabled = enabledItems[itemIndex]
        val progress = pressProgress[itemIndex]

        val dotCenter = pointOnCircle(center, ringRadius, slot, itemCount, rotation.value)
        val dotColor = when {
          !enabled -> disabledDotColor
//          else -> lerp(inactiveDotColor, itemColors[itemIndex], progress)
          else -> effectiveColor(itemIndex)
        }

        val dotRadius = when {
          !enabled -> inactiveDotRadiusPx * 0.82f
          else -> lerpFloat(inactiveDotRadiusPx, activeDotRadiusPx, progress)
        }

        drawCircle(
          color = dotColor,
          radius = dotRadius,
          center = dotCenter,
        )

        val labelColor = when {
          !enabled -> disabledLabelColor
          progress > 0.45f -> lerp(inactiveLabelColor, activeLabelColor, progress)
          else -> inactiveLabelColor
        }

        val labelLayout = measureFifthsCircleLabel(
          textMeasurer = textMeasurer,
          label = itemNames[itemIndex],
          style = labelStyle.copy(color = labelColor),
          accidentalTuckPx = accidentalTuckPx,
        )
        val angle = angleForCircleSlot(slot, itemCount, rotation.value)
        val labelRadius = fifthsCircleLabelRadiusPx(
          ringRadiusPx = ringRadius,
          minimumInsetPx = labelInsetPx,
          labelWidthPx = labelLayout.inkBounds.width,
          labelHeightPx = labelLayout.inkBounds.height,
          angleRadians = angle,
          dotRadiusPx = max(inactiveDotRadiusPx, activeDotRadiusPx),
          dotGapPx = labelRingGapPx,
          cardinalTuckPx = cardinalLabelTuckPx,
        ) +
          labelLayout.leftDiagonalRadiusNudgePx * leftDiagonalAxisFactor(angle) +
          labelLayout.rightSideRadiusNudgePx * rightSideAxisFactor(angle) +
          labelLayout.rightDiagonalRadiusNudgePx * rightDiagonalAxisFactor(angle) +
          labelLayout.rightLowerDiagonalRadiusNudgePx * rightLowerDiagonalAxisFactor(angle)
        val labelCenter = pointOnCircle(center, labelRadius, slot, itemCount, rotation.value)
        drawFifthsCircleLabel(
          label = labelLayout,
          center = labelCenter + Offset(
            x =
              labelLayout.verticalCardinalCenterNudgeX * verticalCardinalAxisFactor(angle) +
                labelLayout.rightCardinalCenterNudgeX * rightCardinalAxisFactor(angle) +
                labelLayout.leftCardinalCenterNudgeX * leftCardinalAxisFactor(angle),
            y = 0f,
          ),
        )
      }

      // Transient tap-feedback flashes, drawn on top so correct/incorrect colors read clearly over
      // the resting dots. Reading flashes + each progress here keeps the animation in the draw phase.
      val flashesByIndex = state?.flashes?.groupBy { it.index } ?: emptyMap()
      flashesByIndex.forEach { (itemIndex, flashes) ->
        if (itemIndex !in 0 until itemCount || !enabledItems[itemIndex]) return@forEach
        val slot = visualOrder.indexOf(itemIndex)
        if (slot < 0) return@forEach
        val flashCenter = pointOnCircle(center, ringRadius, slot, itemCount, rotation.value)

        flashes.forEach { flash ->
          val p = flash.progress.value
          if (p <= 0.001f) return@forEach

          drawCircle(
            color = flash.color.copy(alpha = 0.32f * p),
            radius = haloRadiusPx,
            center = flashCenter,
          )
          drawCircle(
            color = flash.color.copy(alpha = 0.9f * p),
            radius = activeDotRadiusPx,
            center = flashCenter,
          )
        }
      }
    }

    if (centerContent != null || onCenterClick != null) {
      val click = onCenterClick

      Box(
        // A clickable center is a fixed-size round button; content-only centers size themselves,
        // so callers can host arbitrary control clusters in the ring's empty middle.
        modifier = if (click != null) {
          Modifier
            .size(centerButtonSize)
            .clip(CircleShape)
            .clickable(onClick = click)
        } else {
          Modifier
        },
        contentAlignment = Alignment.Center,
      ) {
        centerContent?.let { it() }
      }
    }
  }
}

private fun pointOnCircle(
  center: Offset,
  radius: Float,
  slot: Int,
  count: Int,
  rotationRadians: Float = 0f,
): Offset {
  val angle = angleForCircleSlot(slot, count, rotationRadians)

  return Offset(
    x = center.x + cos(angle).toFloat() * radius,
    y = center.y + sin(angle).toFloat() * radius,
  )
}

private fun angleForCircleSlot(
  slot: Int,
  count: Int,
  rotationRadians: Float = 0f,
): Double = -PI / 2.0 + 2.0 * PI * slot.toDouble() / count.toDouble() + rotationRadians

private const val DirectionEpsilon = 0.0001f
private const val CardinalFalloffStart = 0.9659258f // cos(15 degrees)
private const val CardinalLabelTuckFraction = 0.18f
private const val AccidentalVerticalCenterNudgeFraction = 0.45f
private const val SharpHorizontalEdgeNudgeFraction = 0.5f
private const val FlatHorizontalEdgeNudgeFraction = 0.5f
private const val PrefixAccidentalHorizontalEdgeNudgeFraction = 1f
private const val PrefixFlatDiagonalRadiusNudgeFraction = 0.7f
private const val SuffixAccidentalRightSideRadiusNudgeFraction = 0.5f
private const val SuffixAccidentalRightDiagonalRadiusNudgeFraction = 0.5f
private const val SuffixAccidentalRightLowerDiagonalRadiusNudgeFraction = 0.5f
private val RightDiagonalOffsetRadians = PI / 6.0
private val RightDiagonalFalloffRadians = PI / 12.0
private val RightLowerDiagonalOffsetRadians = PI / 3.0
private val RightLowerDiagonalFalloffRadians = PI / 12.0
private val LeftDiagonalOffsetRadians = PI / 6.0
private val LeftDiagonalFalloffRadians = PI / 12.0

private data class MeasuredFifthsCircleLabel(
  val first: TextLayoutResult,
  val second: TextLayoutResult? = null,
  val secondOffset: Offset = Offset.Zero,
  val inkBounds: Rect,
  val verticalCardinalCenterNudgeX: Float = 0f,
  val rightCardinalCenterNudgeX: Float = 0f,
  val leftCardinalCenterNudgeX: Float = 0f,
  val leftDiagonalRadiusNudgePx: Float = 0f,
  val rightSideRadiusNudgePx: Float = 0f,
  val rightDiagonalRadiusNudgePx: Float = 0f,
  val rightLowerDiagonalRadiusNudgePx: Float = 0f,
)

private fun measureFifthsCircleLabel(
  textMeasurer: androidx.compose.ui.text.TextMeasurer,
  label: String,
  style: TextStyle,
  accidentalTuckPx: Float,
): MeasuredFifthsCircleLabel {
  val accidentalParts = accidentalSuffixParts(label)
  if (accidentalParts != null) {
    val (base, accidental) = accidentalParts
    val baseLayout = textMeasurer.measure(text = AnnotatedString(base), style = style)
    val accidentalLayout = textMeasurer.measure(text = AnnotatedString(accidental), style = style)
    return measureTuckedLabel(
      first = baseLayout,
      second = accidentalLayout,
      tuckPx = accidentalTuckPx,
      verticalCardinalCenterNudgeX = accidentalTuckPx * AccidentalVerticalCenterNudgeFraction,
      rightCardinalCenterNudgeX = accidentalSuffixRightCardinalNudgePx(accidental, accidentalTuckPx),
      leftCardinalCenterNudgeX = 0f,
      rightSideRadiusNudgePx = accidentalSuffixRightSideRadiusNudgePx(accidental, accidentalTuckPx),
      rightDiagonalRadiusNudgePx =
        accidentalSuffixRightDiagonalRadiusNudgePx(accidental, accidentalTuckPx),
      rightLowerDiagonalRadiusNudgePx =
        accidentalSuffixRightLowerDiagonalRadiusNudgePx(accidental, accidentalTuckPx),
    )
  }

  val prefixParts = accidentalPrefixParts(label)
  if (prefixParts != null) {
    val (accidental, base) = prefixParts
    val accidentalLayout = textMeasurer.measure(text = AnnotatedString(accidental), style = style)
    val baseLayout = textMeasurer.measure(text = AnnotatedString(base), style = style)
    return measureTuckedLabel(
      first = accidentalLayout,
      second = baseLayout,
      tuckPx = accidentalTuckPx,
      verticalCardinalCenterNudgeX = -accidentalTuckPx * AccidentalVerticalCenterNudgeFraction,
      rightCardinalCenterNudgeX = 0f,
      leftCardinalCenterNudgeX = accidentalPrefixLeftCardinalNudgePx(accidental, accidentalTuckPx),
      leftDiagonalRadiusNudgePx =
        accidentalPrefixLeftDiagonalRadiusNudgePx(accidental, accidentalTuckPx),
    )
  }

  return run {
    val layout = textMeasurer.measure(text = AnnotatedString(label), style = style)
    MeasuredFifthsCircleLabel(first = layout, inkBounds = textInkBounds(layout))
  }
}

private fun measureTuckedLabel(
  first: TextLayoutResult,
  second: TextLayoutResult,
  tuckPx: Float,
  verticalCardinalCenterNudgeX: Float,
  rightCardinalCenterNudgeX: Float,
  leftCardinalCenterNudgeX: Float,
  leftDiagonalRadiusNudgePx: Float = 0f,
  rightSideRadiusNudgePx: Float = 0f,
  rightDiagonalRadiusNudgePx: Float = 0f,
  rightLowerDiagonalRadiusNudgePx: Float = 0f,
): MeasuredFifthsCircleLabel {
  val firstInk = textInkBounds(first)
  val secondInk = textInkBounds(second)
  val secondOffset =
    Offset(
      x = firstInk.right - tuckPx - secondInk.left,
      y = first.getLineBaseline(0) - second.getLineBaseline(0),
    )
  val shiftedSecondInk = secondInk.translateBy(secondOffset)

  return MeasuredFifthsCircleLabel(
    first = first,
    second = second,
    secondOffset = secondOffset,
    inkBounds = union(firstInk, shiftedSecondInk),
    verticalCardinalCenterNudgeX = verticalCardinalCenterNudgeX,
    rightCardinalCenterNudgeX = rightCardinalCenterNudgeX,
    leftCardinalCenterNudgeX = leftCardinalCenterNudgeX,
    leftDiagonalRadiusNudgePx = leftDiagonalRadiusNudgePx,
    rightSideRadiusNudgePx = rightSideRadiusNudgePx,
    rightDiagonalRadiusNudgePx = rightDiagonalRadiusNudgePx,
    rightLowerDiagonalRadiusNudgePx = rightLowerDiagonalRadiusNudgePx,
  )
}

internal fun accidentalSuffixParts(label: String): Pair<String, String>? {
  val accidental = label.lastOrNull() ?: return null
  val base = label.dropLast(1).takeIf { it.isNotEmpty() } ?: return null

  return when (accidental) {
    '♭', '♯', '#' -> base to accidental.toString()
    'b', 'B' ->
      if (base.length == 1 && base.first().uppercaseChar() in 'A'..'G') {
        base to accidental.toString()
      } else {
        null
      }
    else -> null
  }
}

internal fun accidentalPrefixParts(label: String): Pair<String, String>? {
  val accidental = label.firstOrNull() ?: return null
  val base = label.drop(1).takeIf { it.isNotEmpty() } ?: return null

  return when (accidental) {
    '♭', '♯', '#' -> accidental.toString() to base
    'b', 'B' ->
      if (base.first().isDigit()) {
        accidental.toString() to base
      } else {
        null
      }
    else -> null
  }
}

internal fun accidentalPrefixLeftCardinalNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  when (accidental) {
    "♭", "♯", "#", "b", "B" ->
      -accidentalTuckPx * PrefixAccidentalHorizontalEdgeNudgeFraction
    else -> 0f
  }

internal fun accidentalPrefixLeftDiagonalRadiusNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  if (accidental.isFlatAccidental()) {
    accidentalTuckPx * PrefixFlatDiagonalRadiusNudgeFraction
  } else {
    0f
  }

internal fun accidentalSuffixRightCardinalNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  when {
    accidental.isSharpAccidental() -> accidentalTuckPx * SharpHorizontalEdgeNudgeFraction
    accidental.isFlatAccidental() -> accidentalTuckPx * FlatHorizontalEdgeNudgeFraction
    else -> 0f
  }

internal fun accidentalSuffixRightSideRadiusNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  if (accidental.isSharpAccidental() || accidental.isFlatAccidental()) {
    accidentalTuckPx * SuffixAccidentalRightSideRadiusNudgeFraction
  } else {
    0f
  }

internal fun accidentalSuffixRightDiagonalRadiusNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  if (accidental.isSharpAccidental() || accidental.isFlatAccidental()) {
    accidentalTuckPx * SuffixAccidentalRightDiagonalRadiusNudgeFraction
  } else {
    0f
  }

internal fun accidentalSuffixRightLowerDiagonalRadiusNudgePx(
  accidental: String,
  accidentalTuckPx: Float,
): Float =
  if (accidental.isSharpAccidental() || accidental.isFlatAccidental()) {
    accidentalTuckPx * SuffixAccidentalRightLowerDiagonalRadiusNudgeFraction
  } else {
    0f
  }

private fun DrawScope.drawFifthsCircleLabel(
  label: MeasuredFifthsCircleLabel,
  center: Offset,
) {
  val origin = center - label.inkBounds.center
  drawText(textLayoutResult = label.first, topLeft = origin)
  label.second?.let {
    drawText(textLayoutResult = it, topLeft = origin + label.secondOffset)
  }
}

internal fun fifthsCircleLabelRadiusPx(
  ringRadiusPx: Float,
  minimumInsetPx: Float,
  labelWidthPx: Float,
  labelHeightPx: Float,
  angleRadians: Double,
  dotRadiusPx: Float,
  dotGapPx: Float,
  cardinalTuckPx: Float = 0f,
): Float {
  val outwardHalfExtent = radialHalfExtentPx(
    widthPx = labelWidthPx,
    heightPx = labelHeightPx,
    angleRadians = angleRadians,
  )
  val inset = if (dotRadiusPx > 0f || dotGapPx > 0f) {
    dotRadiusPx + dotGapPx + outwardHalfExtent
  } else {
    max(minimumInsetPx, outwardHalfExtent)
  }
  val opticalInset = max(0f, inset - cardinalTuckPx * cardinalAxisFactor(angleRadians))
  return max(0f, ringRadiusPx - opticalInset)
}

internal fun radialHalfExtentPx(
  widthPx: Float,
  heightPx: Float,
  angleRadians: Double,
): Float {
  if (widthPx <= 0f || heightPx <= 0f) return 0f

  val horizontal = abs(cos(angleRadians)).toFloat()
  val vertical = abs(sin(angleRadians)).toFloat()

  val halfWidth = widthPx / 2f
  val halfHeight = heightPx / 2f
  val denominator =
    (horizontal * horizontal) / (halfWidth * halfWidth) +
      (vertical * vertical) / (halfHeight * halfHeight)

  return if (denominator > DirectionEpsilon) {
    1f / sqrt(denominator)
  } else {
    0f
  }
}

internal fun cardinalAxisFactor(angleRadians: Double): Float {
  val axis = max(abs(cos(angleRadians)).toFloat(), abs(sin(angleRadians)).toFloat())
  return axisProximityFactor(axis)
}

internal fun verticalCardinalAxisFactor(angleRadians: Double): Float =
  axisProximityFactor(abs(sin(angleRadians)).toFloat())

internal fun rightCardinalAxisFactor(angleRadians: Double): Float =
  axisProximityFactor(cos(angleRadians).toFloat())

internal fun rightSideAxisFactor(angleRadians: Double): Float =
  cos(angleRadians).toFloat().coerceIn(0f, 1f)

internal fun rightDiagonalAxisFactor(angleRadians: Double): Float {
  val distanceFromRight = angularDistanceRadians(angleRadians, 0.0)
  val distanceFromTarget = abs(distanceFromRight - RightDiagonalOffsetRadians)
  return (1.0 - distanceFromTarget / RightDiagonalFalloffRadians).coerceIn(0.0, 1.0).toFloat()
}

internal fun rightLowerDiagonalAxisFactor(angleRadians: Double): Float {
  val distanceFromTarget = angularDistanceRadians(angleRadians, RightLowerDiagonalOffsetRadians)
  return (1.0 - distanceFromTarget / RightLowerDiagonalFalloffRadians).coerceIn(0.0, 1.0).toFloat()
}

internal fun leftCardinalAxisFactor(angleRadians: Double): Float =
  axisProximityFactor(-cos(angleRadians).toFloat())

internal fun leftDiagonalAxisFactor(angleRadians: Double): Float {
  val distanceFromLeft = angularDistanceRadians(angleRadians, PI)
  val distanceFromTarget = abs(distanceFromLeft - LeftDiagonalOffsetRadians)
  return (1.0 - distanceFromTarget / LeftDiagonalFalloffRadians).coerceIn(0.0, 1.0).toFloat()
}

private fun axisProximityFactor(axisComponent: Float): Float =
  ((axisComponent - CardinalFalloffStart) / (1f - CardinalFalloffStart)).coerceIn(0f, 1f)

private fun angularDistanceRadians(
  first: Double,
  second: Double,
): Double {
  val twoPi = 2.0 * PI
  var delta = (first - second) % twoPi
  if (delta > PI) delta -= twoPi
  if (delta < -PI) delta += twoPi
  return abs(delta)
}

private fun String.isSharpAccidental(): Boolean = this == "♯" || this == "#"

private fun String.isFlatAccidental(): Boolean = this == "♭" || this == "b" || this == "B"

private fun textInkBounds(layout: TextLayoutResult): Rect {
  if (layout.layoutInput.text.text.isEmpty()) {
    return Rect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat())
  }

  var left = Float.POSITIVE_INFINITY
  var top = Float.POSITIVE_INFINITY
  var right = Float.NEGATIVE_INFINITY
  var bottom = Float.NEGATIVE_INFINITY

  layout.layoutInput.text.text.indices.forEach { offset ->
    val box = layout.getBoundingBox(offset)
    left = min(left, box.left)
    top = min(top, box.top)
    right = max(right, box.right)
    bottom = max(bottom, box.bottom)
  }

  if (left == Float.POSITIVE_INFINITY) {
    return Rect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat())
  }
  return Rect(left, top, right, bottom)
}

private fun Rect.translateBy(offset: Offset): Rect =
  Rect(
    left = left + offset.x,
    top = top + offset.y,
    right = right + offset.x,
    bottom = bottom + offset.y,
  )

private fun union(first: Rect, second: Rect): Rect =
  Rect(
    left = min(first.left, second.left),
    top = min(first.top, second.top),
    right = max(first.right, second.right),
    bottom = max(first.bottom, second.bottom),
  )

private fun lerpFloat(
  start: Float,
  stop: Float,
  fraction: Float,
): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)

internal fun fifthsCircleRingRadiusPx(
  minSidePx: Float,
  outerPaddingPx: Float,
  dotRadiusPx: Float,
  dotEdgePaddingPx: Float?,
): Float {
  val padding = if (dotEdgePaddingPx != null) {
    dotEdgePaddingPx + dotRadiusPx
  } else {
    outerPaddingPx
  }

  return max(0f, minSidePx / 2f - padding)
}

private tailrec fun gcd(a: Int, b: Int): Int {
  return if (b == 0) abs(a) else gcd(b, a % b)
}
