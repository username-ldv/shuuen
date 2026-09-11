package ldv.shuuen.core.audio.engine

interface SoundFontProvider {
  suspend fun loadSoundFont(location: String): Int
  suspend fun loadDefaultSoundFont(): Int
}