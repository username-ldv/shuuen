package ldv.shuuen.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import org.koin.compose.koinInject

/**
 * A small corner icon shown only while a hardware MIDI keyboard is connected and ready to answer.
 * Meant for a top app bar's [ShuuenTopAppBar] statusContent slot; renders nothing otherwise.
 */
@Composable
fun MidiKeyboardBadge(modifier: Modifier = Modifier) {
  val midiKeyboardInput = koinInject<MidiKeyboardInput>()
  val devices by midiKeyboardInput.connectedDevices.collectAsStateWithLifecycle()
  if (devices.isEmpty()) return

  Icon(
    imageVector = Icons.Rounded.Piano,
    contentDescription = "MIDI keyboard connected: ${devices.joinToString()}",
    tint = ShuuenUi.Correct,
    modifier = modifier.padding(end = 10.dp).size(20.dp),
  )
}
