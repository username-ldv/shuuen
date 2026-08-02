package ldv.shuuen.features.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.ui.components.AccountBadge
import ldv.shuuen.core.ui.components.BackendStatusBadge
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.LinearTrainingProgress
import ldv.shuuen.core.ui.components.MidiKeyboardBadge
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import ldv.shuuen.features.training.common.TrainingFlow
import org.jetbrains.compose.resources.painterResource
import shuuen.shared.generated.resources.Res
import shuuen.shared.generated.resources.shuuen_main_logo

@Composable
fun MainMenuScreen(
  viewModel: MainMenuViewModel,
  onStartLevel: (TrainingFlow, String) -> Unit,
  onOpenFreePlay: () -> Unit,
  onOpenMelodies: () -> Unit,
  onOpenSingles: () -> Unit,
  onOpenChords: () -> Unit,
  onOpenPitchSlide: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAccount: () -> Unit,
  onRefreshBackend: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { onRefreshBackend() }

  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        trailingIcon = Icons.Rounded.Settings,
        onTrailingClick = onOpenSettings,
        statusContent = {
          BackendStatusBadge(onClick = onRefreshBackend)
          AccountBadge(onClick = onOpenAccount)
          MidiKeyboardBadge()
        },
        type = ShuuenTopAppBarType.Simple
      )
    },
  ) {
    Box(
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          painter = painterResource(Res.drawable.shuuen_main_logo),
          contentDescription = "Shuuen",
          modifier = Modifier.fillMaxWidth(0.62f),
          contentScale = ContentScale.Fit,
          // The asset's strokes are white; tint follows the theme so the logo
          // reads on light backgrounds too.
          colorFilter = ColorFilter.tint(ShuuenUi.Ink),
        )
        Text(
          text = "The last ear trainer app you'll need.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.titleSmall,
          textAlign = TextAlign.Center,
        )
      }
    }

    state.continueCard?.let { continueCard ->
      ContinueCard(
        state = continueCard,
        onContinue = { onStartLevel(continueCard.flow, continueCard.levelReference) },
        onNext = { nextReference -> onStartLevel(continueCard.flow, nextReference) },
      )
    }

    ExerciseList(
      onOpenSingles = onOpenSingles,
      onOpenMelodies = onOpenMelodies,
      onOpenChords = onOpenChords,
      onOpenPitchSlide = onOpenPitchSlide,
      onOpenFreePlay = onOpenFreePlay,
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
      FooterLink("LIBRARY", Icons.AutoMirrored.Rounded.LibraryBooks)
      FooterLink("STATISTICS", Icons.Rounded.BarChart)
      FooterLink("POCKET", Icons.Rounded.ModeNight)
    }
  }
}

@Composable
private fun ContinueCard(
  state: ContinueCardState,
  onContinue: () -> Unit,
  onNext: (String) -> Unit,
) {
  var groupsExpanded by rememberSaveable(state.levelReference) { mutableStateOf(false) }
  val course = state.course
  val currentGroup = course?.currentGroup
  val otherGroups = course?.groups.orEmpty().filterNot { it.id == currentGroup?.id }

  SurfaceCard(verticalSpacing = Arrangement.spacedBy(0.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onContinue),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "CONTINUE",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = ShuuenUi.labelSpacing,
            fontWeight = FontWeight.SemiBold,
          ),
        )
        Text(
          text = "${state.flow.label} — ${state.levelName}",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        course?.let {
          Text(
            text = it.name,
            color = ShuuenUi.Muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = "Continue last played level",
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(28.dp),
      )
    }

    when {
      state.isLoadingCourse ->
        Text(
          text = "Loading course progress…",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(top = 10.dp),
        )
      course != null -> {
        currentGroup?.let { group ->
          ProgressBlock(
            label = group.name,
            progress = group.progress,
            modifier = Modifier.padding(top = 10.dp),
          )
        }

        if (course.groups.size > 1) {
          ProgressBlock(
            label = "Course total",
            progress = course.total,
            modifier = Modifier.padding(top = 10.dp),
          )
        } else if (currentGroup == null) {
          ProgressBlock(
            label = "Course total",
            progress = course.total,
            modifier = Modifier.padding(top = 10.dp),
          )
        }

        AnimatedVisibility(
          visible = groupsExpanded && otherGroups.isNotEmpty(),
          enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = tween(220),
            expandFrom = Alignment.Top,
          ),
          exit = fadeOut(tween(140)) + shrinkVertically(
            animationSpec = tween(200),
            shrinkTowards = Alignment.Top,
          ),
        ) {
          Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Hairline()
            otherGroups.forEach { group ->
              ProgressBlock(label = group.name, progress = group.progress, compact = true)
            }
          }
        }
      }
      state.isCourseLevel ->
        Text(
          text = "Course progress unavailable",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(top = 10.dp),
        )
      else ->
        Text(
          text = "Local level",
          color = ShuuenUi.Dim,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (otherGroups.isNotEmpty() || state.nextLevelReference != null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (otherGroups.isNotEmpty()) {
          TextButton(onClick = { groupsExpanded = !groupsExpanded }) {
            Text(if (groupsExpanded) "HIDE GROUPS" else "ALL GROUPS")
            Icon(
              imageVector = if (groupsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Spacer(Modifier.weight(1f))
        state.nextLevelReference?.let { nextReference ->
          TextButton(onClick = { onNext(nextReference) }) {
            Text("NEXT LEVEL")
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProgressBlock(
  label: String,
  progress: CompletionProgress,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        color = if (compact) ShuuenUi.Muted else ShuuenUi.Text,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${progress.percentage}% complete",
        color = ShuuenUi.Dim,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
      )
    }
    LinearTrainingProgress(progress = progress.fraction)
  }
}

private val TrainingFlow.label: String
  get() = when (this) {
    TrainingFlow.Singles -> "Singles"
    TrainingFlow.Melodies -> "Melodies"
    TrainingFlow.Chords -> "Chords"
  }

@Composable
private fun ExerciseList(
  onOpenSingles: () -> Unit,
  onOpenMelodies: () -> Unit,
  onOpenChords: () -> Unit,
  onOpenPitchSlide: () -> Unit,
  onOpenFreePlay: () -> Unit,
) {
  SurfaceCard(
    contentPadding = PaddingValues(0.dp),
    verticalSpacing = Arrangement.spacedBy(0.dp),
  ) {
    ExerciseRow(
      title = "SINGLES",
      subtitle = "Identify single notes / degrees.",
      icon = Icons.Rounded.MusicNote,
      onClick = onOpenSingles,
    )
    Hairline(Modifier.padding(horizontal = 18.dp))
    ExerciseRow(
      title = "MELODIES",
      subtitle = "Transcribe melodies.",
      icon = Icons.AutoMirrored.Rounded.QueueMusic,
      onClick = onOpenMelodies,
    )
    Hairline(Modifier.padding(horizontal = 18.dp))
    ExerciseRow(
      title = "CHORDS",
      subtitle = "Identify single chords.",
      icon = Icons.Rounded.GraphicEq,
      onClick = onOpenChords,
    )
    Hairline(Modifier.padding(horizontal = 18.dp))
    ExerciseRow(
      title = "PROGRESSIONS",
      subtitle = "Identify chord progressions.",
      icon = Icons.Rounded.BarChart,
    )
    Hairline(Modifier.padding(horizontal = 18.dp))
    ExerciseRow(
      title = "PITCH SLIDE",
      subtitle = "Recreate a tone by sliding the pitch (PoC).",
      icon = Icons.Rounded.Waves,
      onClick = onOpenPitchSlide,
    )
    Hairline(Modifier.padding(horizontal = 18.dp))
    ExerciseRow(
      title = "FREE PLAY",
      subtitle = "Play freely without scoring.",
      icon = Icons.Rounded.Keyboard,
      onClick = onOpenFreePlay,
    )
  }
}

@Composable
private fun ExerciseRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onClick: (() -> Unit)? = null,
) {
  val enabled = onClick != null
  Row(
    modifier = Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 18.dp, vertical = 15.dp)
      .alpha(if (enabled) 1f else 0.38f),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = ShuuenUi.Text,
      modifier = Modifier.size(24.dp),
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        color = ShuuenUi.Text,
        style = MaterialTheme.typography.titleMedium.copy(
          letterSpacing = ShuuenUi.titlesSpacing,
          fontWeight = FontWeight.SemiBold,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        color = ShuuenUi.Muted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
      contentDescription = null,
      tint = ShuuenUi.Dim,
      modifier = Modifier.size(26.dp),
    )
  }
}

@Composable
private fun FooterLink(
  text: String,
  icon: ImageVector,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(icon, contentDescription = null, tint = ShuuenUi.Dim, modifier = Modifier.size(15.dp))
    Text(
      text = text,
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = ShuuenUi.labelSpacing),
      maxLines = 1,
    )
  }
}
