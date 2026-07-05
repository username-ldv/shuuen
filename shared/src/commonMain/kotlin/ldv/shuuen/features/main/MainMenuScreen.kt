package ldv.shuuen.features.main

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ldv.shuuen.core.ui.components.Hairline
import ldv.shuuen.core.ui.components.LinearTrainingProgress
import ldv.shuuen.core.ui.components.MidiKeyboardBadge
import ldv.shuuen.core.ui.components.ShuuenTopAppBar
import ldv.shuuen.core.ui.components.ShuuenTopAppBarType
import ldv.shuuen.core.ui.components.ShuuenUi
import ldv.shuuen.core.ui.components.StaticScreenFrame
import ldv.shuuen.core.ui.components.SurfaceCard
import org.jetbrains.compose.resources.painterResource
import shuuen.shared.generated.resources.Res
import shuuen.shared.generated.resources.shuuen_main_logo

@Composable
fun MainMenuScreen(
  onOpenFreePlay: () -> Unit,
  onOpenMelodies: () -> Unit,
  onOpenSingles: () -> Unit,
  onOpenChords: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  StaticScreenFrame(
    topBar = {
      ShuuenTopAppBar(
        trailingIcon = Icons.Rounded.Settings,
        onTrailingClick = onOpenSettings,
        statusContent = { MidiKeyboardBadge() },
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
        )
        Text(
          text = "The last ear trainer app you need.",
          color = ShuuenUi.Muted,
          style = MaterialTheme.typography.titleSmall,
          textAlign = TextAlign.Center,
        )
      }
    }

    ContinueCard()

    ExerciseList(
      onOpenSingles = onOpenSingles,
      onOpenMelodies = onOpenMelodies,
      onOpenChords = onOpenChords,
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
private fun ContinueCard() {
  SurfaceCard(verticalSpacing = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
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
          text = "Singles — D Major — 20 questions",
          color = ShuuenUi.Text,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = ShuuenUi.Dim,
        modifier = Modifier.size(28.dp),
      )
    }
    LinearTrainingProgress(progress = 0.68f)
    Text(
      text = "68% complete",
      color = ShuuenUi.Dim,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1,
    )
  }
}

@Composable
private fun ExerciseList(
  onOpenSingles: () -> Unit,
  onOpenMelodies: () -> Unit,
  onOpenChords: () -> Unit,
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
