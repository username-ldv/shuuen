package ldv.shuuen.data.settings

import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import ldv.shuuen.domain.audio.midi.MidiChannel
import ldv.shuuen.domain.audio.midi.Preset
import ldv.shuuen.domain.repository.AppSettings
import ldv.shuuen.domain.repository.SettingsRepository
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

  override suspend fun setSoundFontPath(path: String?) {
    store.update { it?.copy(soundFontPath = path) }
  }
}
