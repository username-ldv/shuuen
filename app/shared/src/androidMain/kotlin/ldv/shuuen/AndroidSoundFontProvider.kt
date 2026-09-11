package ldv.shuuen

import android.content.Context
import android.net.Uri
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
import ldv.shuuen.core.audio.engine.SoundFontProvider

internal class AndroidSoundFontProvider(
  private val context: Context,
) : SoundFontProvider {
  override suspend fun loadSoundFont(location: String): Int {
    val descriptor = runCatching {
      context.contentResolver.openFileDescriptor(Uri.parse(location), "r")
    }.getOrNull()
    return if (descriptor == null) {
      BASSMIDI.BASS_MIDI_FontInit(location, 0)
    } else {
      BASSMIDI.BASS_MIDI_FontInit(descriptor, 0)
    }
  }

  override suspend fun loadDefaultSoundFont(): Int {
    return BASSMIDI.BASS_MIDI_FontInit(BASS.Asset(context.assets, DEFAULT_SOUNDFONT_ASSET), 0)
  }

  private companion object {
    const val DEFAULT_SOUNDFONT_ASSET = "GeneralUser-GS.sf2"
  }
}