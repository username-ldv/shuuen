package ldv.shuuen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import ldv.shuuen.app.App

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    // Required so FileKit's file picker can attach to this activity.
    FileKit.init(this)

    setContent {
      App()
    }
  }
}