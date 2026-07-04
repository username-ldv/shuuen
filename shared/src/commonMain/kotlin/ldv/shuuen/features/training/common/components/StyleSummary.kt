package ldv.shuuen.features.training.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.music.NoteValue
import ldv.shuuen.core.music.generator.ChordFigure
import ldv.shuuen.core.music.generator.ChordStyle
import ldv.shuuen.core.music.generator.FigureLadder
import ldv.shuuen.core.music.generator.MelodyStyle
import ldv.shuuen.core.music.generator.NoteWeights
import ldv.shuuen.core.music.generator.RhythmFigure
import ldv.shuuen.core.ui.components.ShuuenUi

/**
 * Compact, informational summary of a melody style: one chip per rhythm figure — note heads,
 * stems, beams and dots drawn directly (no reliance on musical glyphs in the platform fonts),
 * with the heads rising/falling along the figure's contour — plus a line describing the note
 * picker. A chip's opacity reflects the figure's weight; chord-ladder (arpeggio) figures carry
 * an underline in the accent color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MelodyStyleSummary(style: MelodyStyle, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val maxWeight = style.figures.maxOf { it.weight }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      style.figures.forEach { weighted ->
        RhythmFigureChip(
          figure = weighted.figure,
          emphasis = (weighted.weight / maxWeight).toFloat(),
        )
      }
    }
    Text(
      text = notePickerLabel(style.noteWeights),
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

/**
 * Compact summary of a chord style: one chip per figure — stacked-dot glyph plus the shape as
 * scale steps from the bass ("1·3·5" root triad, "1·4·6" second inversion, …), or a dice for
 * the free pick. A chip's opacity reflects the figure's weight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordStyleSummary(style: ChordStyle, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val maxWeight = style.figures.maxOf { it.weight }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      style.figures.forEach { weighted ->
        ChordFigureChip(
          figure = weighted.figure,
          emphasis = (weighted.weight / maxWeight).toFloat(),
        )
      }
    }
    Text(
      text = chordFiguresLabel(style),
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

private fun notePickerLabel(weights: NoteWeights): String {
  val base =
    if (weights.intervalWeights.isEmpty() && weights.degreeWeights.isEmpty()) {
      "Uniform random note picking"
    } else {
      "Weighted toward stepwise, singable motion"
    }
  val boost = weights.chordToneBoost
  return if (boost != 1.0) "$base · pulled to the context chord (×${boost.trimmed()})" else base
}

private fun chordFiguresLabel(style: ChordStyle): String {
  val hasStacked = style.figures.any { it.figure is ChordFigure.Stacked }
  val hasFree = style.figures.any { it.figure is ChordFigure.FreePick }
  return when {
    hasStacked && hasFree -> "On-scale stacked shapes with occasional free random picks"
    hasStacked -> "On-scale stacked shapes, weighted as shown"
    else -> "Fully random note stacks"
  }
}

private fun Double.trimmed(): String =
  if (this % 1.0 == 0.0) toInt().toString() else toString()

/** Opacity floor keeps even the rarest figure readable while the weighting stays visible. */
private fun chipAlpha(emphasis: Float): Float = 0.4f + 0.5f * emphasis.coerceIn(0f, 1f)

// region Rhythm figure glyphs

private val HeadRx = 3.4.dp
private val HeadRy = 2.5.dp
private val StemHeight = 11.dp
private val NoteSpacing = 11.dp
private val StepRise = 2.5.dp
private val GlyphHeight = 32.dp

@Composable
private fun RhythmFigureChip(figure: RhythmFigure, emphasis: Float) {
  val color = Color.White.copy(alpha = chipAlpha(emphasis))
  val isArpeggio = figure.ladder == FigureLadder.Chord
  Column(
    modifier = Modifier
      .clip(ShuuenUi.ControlShape)
      .background(Color.White.copy(alpha = 0.05f))
      .padding(horizontal = 9.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    RhythmFigureGlyph(figure, color)
    if (isArpeggio) {
      // Marks figures that arpeggiate the context chord instead of walking the scale.
      Canvas(modifier = Modifier.width(16.dp).height(2.dp)) {
        drawLine(
          color = ShuuenUi.Text.copy(alpha = 0.6f),
          start = Offset(0f, size.height / 2),
          end = Offset(size.width, size.height / 2),
          strokeWidth = size.height,
        )
      }
    }
  }
}

@Composable
private fun RhythmFigureGlyph(figure: RhythmFigure, color: Color) {
  val notes = figure.values.size
  val width = 10.dp + NoteSpacing * (notes - 1) + 12.dp
  Canvas(modifier = Modifier.width(width).height(GlyphHeight)) {
    // Head heights follow the contour cumulatively; free (null) gaps stay level.
    val offsets = buildList {
      var current = 0
      add(0)
      figure.contour.forEach { step ->
        current += step ?: 0
        add(current)
      }
    }
    val padded = offsets + List(notes - offsets.size) { offsets.last() }
    val minOff = padded.min()
    val maxOff = padded.max()

    val rise = StepRise.toPx()
    val headRy = HeadRy.toPx()
    val stem = StemHeight.toPx()
    // The lowest head sits just above the bottom edge; shrink nothing, the presets' contours fit.
    val baseY = size.height - headRy - 2.dp.toPx() + minOff * rise
    fun headCenter(i: Int) = Offset(
      x = 5.dp.toPx() + NoteSpacing.toPx() * i + HeadRx.toPx(),
      y = baseY - padded[i] * rise,
    )

    figure.values.forEachIndexed { i, value ->
      drawNote(headCenter(i), value, color)
    }

    // Beams join adjacent runs of equal eighths/sixteenths; a lone one gets a flag instead.
    var runStart = 0
    for (i in 1..notes) {
      val runEnds = i == notes || figure.values[i] != figure.values[runStart]
      if (!runEnds) continue
      val value = figure.values[runStart]
      val beams = when (value) {
        NoteValue.Eighth -> 1
        NoteValue.Sixteenth -> 2
        else -> 0
      }
      if (beams > 0) {
        val stemTop = { n: Int -> headCenter(n) + Offset(HeadRx.toPx(), -stem) }
        if (i - runStart >= 2) {
          repeat(beams) { beam ->
            val drop = Offset(0f, beam * 3.2.dp.toPx())
            drawLine(
              color = color,
              start = stemTop(runStart) + drop,
              end = stemTop(i - 1) + drop,
              strokeWidth = 2.2.dp.toPx(),
            )
          }
        } else {
          repeat(beams) { flag ->
            val top = stemTop(runStart) + Offset(0f, flag * 3.2.dp.toPx())
            drawLine(
              color = color,
              start = top,
              end = top + Offset(4.5.dp.toPx(), 3.dp.toPx()),
              strokeWidth = 1.6.dp.toPx(),
            )
          }
        }
      }
      runStart = i
    }
  }
}

/** One note: the head (hollow for half/whole), an upward stem, and a dot when dotted. */
private fun DrawScope.drawNote(center: Offset, value: NoteValue, color: Color) {
  val rx = HeadRx.toPx()
  val ry = HeadRy.toPx()
  val hollow = value == NoteValue.Half || value == NoteValue.Whole
  drawOval(
    color = color,
    topLeft = Offset(center.x - rx, center.y - ry),
    size = Size(rx * 2, ry * 2),
    style = if (hollow) Stroke(width = 1.4.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill,
  )
  if (value != NoteValue.Whole) {
    drawLine(
      color = color,
      start = Offset(center.x + rx, center.y),
      end = Offset(center.x + rx, center.y - StemHeight.toPx()),
      strokeWidth = 1.4.dp.toPx(),
    )
  }
  if (value == NoteValue.DottedQuarter) {
    drawCircle(color = color, radius = 1.3.dp.toPx(), center = center + Offset(rx + 3.dp.toPx(), 0f))
  }
}

// endregion

// region Chord figure glyphs

@Composable
private fun ChordFigureChip(figure: ChordFigure, emphasis: Float) {
  val color = Color.White.copy(alpha = chipAlpha(emphasis))
  Row(
    modifier = Modifier
      .clip(ShuuenUi.ControlShape)
      .background(Color.White.copy(alpha = 0.05f))
      .padding(horizontal = 9.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    when (figure) {
      is ChordFigure.FreePick -> {
        Icon(
          Icons.Rounded.Casino,
          contentDescription = null,
          tint = color,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Random",
          color = color,
          style = MaterialTheme.typography.labelMedium,
        )
      }

      is ChordFigure.Stacked -> {
        ChordShapeGlyph(figure.ladderSteps, color)
        Text(
          // The shape as scale steps from the bass: [0, 2, 4] reads "1·3·5".
          text = figure.ladderSteps.joinToString("·") { "${it + 1}" },
          color = color,
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
  }
}

@Composable
private fun ChordShapeGlyph(ladderSteps: List<Int>, color: Color) {
  Canvas(modifier = Modifier.width(10.dp).height(22.dp)) {
    val maxStep = ladderSteps.last().coerceAtLeast(1)
    val usable = size.height - 6.dp.toPx()
    ladderSteps.forEach { step ->
      drawCircle(
        color = color,
        radius = 2.2.dp.toPx(),
        center = Offset(size.width / 2, size.height - 3.dp.toPx() - usable * step / maxStep),
      )
    }
  }
}

// endregion
