package ldv.shuuen

import ldv.shuuen.bass.Bass
import ldv.shuuen.core.audio.engine.SoundFontProvider
import java.nio.file.Path
import kotlin.io.path.exists

internal class DesktopSoundFontProvider : SoundFontProvider {
  override suspend fun loadSoundFont(location: String): Int =
    Bass.loadSoundFont(location)

  override suspend fun loadDefaultSoundFont(): Int {
    val path = externalDefaultSoundFontPath()
      ?: error("Default soundfont file was not found.")
    return Bass.loadSoundFont(path.toString())
  }

  private fun externalDefaultSoundFontPath(): Path? {
    val resourceDir = System.getProperty("compose.application.resources.dir")
    return listOfNotNull(
      resourceDir?.let { Path.of(it, "soundfonts", DEFAULT_SOUNDFONT_FILE) },
      Path.of(
        "desktopApp",
        "src",
        "main",
        "appResources",
        "common",
        "soundfonts",
        DEFAULT_SOUNDFONT_FILE
      ),
      Path.of("src", "main", "appResources", "common", "soundfonts", DEFAULT_SOUNDFONT_FILE),
    ).firstOrNull { it.exists() }
  }

  private companion object {
    const val DEFAULT_SOUNDFONT_FILE = "GeneralUser-GS.sf2"
  }
}