package ldv.shuuen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StaticScreenFrame(
  modifier: Modifier = Modifier.Companion,
  horizontalPadding: Dp = 20.dp,
  topPadding: Dp = 0.dp,
  bottomPadding: Dp = 16.dp,
  maxWidth: Dp = 560.dp,
  verticalSpacing: Dp = 14.dp,
  scrollable: Boolean = true,
  topBar: @Composable () -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    modifier = modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.TopCenter,
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentColor = ldv.shuuen.core.ui.components.ShuuenUi.Text,
      topBar = topBar,
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        val scrollState = rememberScrollState()
        var columnModifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight()
          .navigationBarsPadding()

        if (scrollable) {
          columnModifier = columnModifier.verticalScroll(scrollState)
        }

        Column(
          modifier = columnModifier.padding(
            horizontalPadding,
            topPadding,
            horizontalPadding,
            bottomPadding
          ),
          verticalArrangement = Arrangement.spacedBy(verticalSpacing),
          content = content,
        )
      }
    }
  }
}
