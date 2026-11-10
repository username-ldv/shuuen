package ldv.shuuen.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.minus
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Opacity of the scrim behind the top bar while content is scrolled beneath it. */
private const val TopBarScrimAlpha = 0.85f

@Composable
fun StaticScreenFrame(
  modifier: Modifier = Modifier,
  horizontalPadding: Dp = 20.dp,
  topPadding: Dp = 0.dp,
  bottomPadding: Dp = 0.dp,
  maxWidth: Dp = 560.dp,
  verticalSpacing: Dp = 14.dp,
  scrollable: Boolean = true,
  topBar: @Composable () -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  val scrollState = rememberScrollState()
  val background = MaterialTheme.colorScheme.background
  // The top bar stays transparent at rest; once content scrolls up beneath it, a
  // translucent scrim fades in so the bar's icons stay readable over that content.
  val scrimAlpha by
    animateFloatAsState(
      targetValue = if (scrollable && scrollState.canScrollBackward) TopBarScrimAlpha else 0f,
      label = "topBarScrim",
    )

  Box(
    modifier = modifier.fillMaxSize().background(background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentColor = ShuuenUi.Text,
      topBar = {
        Box(Modifier.drawBehind { drawRect(background.copy(alpha = scrimAlpha)) }) { topBar() }
      },
    ) { innerPadding ->
      // The top inset is applied inside the scrolling column, not around it, so content
      // scrolls up under the top bar instead of being clipped at the bar's bottom edge.
      val topInset = innerPadding.calculateTopPadding()
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(
              innerPadding -
                WindowInsets.navigationBars.asPaddingValues() -
                PaddingValues(top = topInset)
            ),
        contentAlignment = Alignment.TopCenter,
      ) {
        var columnModifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight()

        if (scrollable) {
          columnModifier = columnModifier.verticalScroll(scrollState)
        }

        Column(
          modifier =
            columnModifier.padding(
              horizontalPadding,
              topInset + topPadding,
              horizontalPadding,
              bottomPadding,
            ),
          verticalArrangement = Arrangement.spacedBy(verticalSpacing),
          content = {
            content()
            Spacer(
              Modifier.padding(
                WindowInsets.navigationBars.asPaddingValues() - PaddingValues(verticalSpacing)
              )
            )
          },
        )
      }
    }
  }
}
