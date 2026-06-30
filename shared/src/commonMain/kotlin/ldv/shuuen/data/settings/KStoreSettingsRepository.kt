package ldv.shuuen.data.settings

import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.SettingsRepository
import org.koin.core.annotation.Named

class KStoreSettingsRepository(
  @Named("files") path: Path
) : SettingsRepository {
  val store = storeOf(file = Path(path, "settings.json"), default = AppSettings())
  override val settings: Flow<AppSettings> = store.updates.map { it ?: AppSettings() }

  override suspend fun setPreset(channel: MidiChannel, preset: Preset) {
    store.update {
      it?.copy(
        presets = when (channel) {
          MidiChannel.Notes -> it.presets.copy(notes = preset)
          MidiChannel.Drone -> it.presets.copy(drone = preset)
          MidiChannel.Cadence -> it.presets.copy(cadence = preset)
        },
      )
    }
  }

  override suspend fun setVolume(channel: MidiChannel, value: Int) {
    store.update {
      it?.copy(
        volumes = when (channel) {
          MidiChannel.Notes -> it.volumes.copy(notes = value)
          MidiChannel.Drone -> it.volumes.copy(drone = value)
          MidiChannel.Cadence -> it.volumes.copy(cadence = value)
        },
      )
    }
  }

  override suspend fun setMelodyOriginalVolumeBoost(value: Int) {
    store.update { it?.copy(melodyOriginalVolumeBoost = value.coerceIn(0, MidiValueMax)) }
  }

  override suspend fun setInputMethod(inputMethod: InputMethod) {
    store.update { it?.copy(inputMethod = inputMethod) }
  }

  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) {
    store.update { it?.copy(allowSevenAccidentalKeys = value) }
  }

  override suspend fun setNoteNames(names: List<String>) {
    store.update { settings ->
      settings?.copy(musicLabels = settings.musicLabels.copy(noteNames = names))
    }
  }

  override suspend fun setDegreeNames(names: List<String>) {
    store.update { settings ->
      settings?.copy(musicLabels = settings.musicLabels.copy(degreeNames = names))
    }
  }

  override suspend fun setSoundFontPath(path: String?) {
    store.update { it?.copy(soundFontPath = path) }
  }

  private companion object {
    const val MidiValueMax = 127
  }
}
