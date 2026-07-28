package ldv.shuuen.data.settings

import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.ThemeSettings
import ldv.shuuen.core.settings.coerceLevelStatsWindow
import org.koin.core.annotation.Named

class KStoreSettingsRepository(
  @Named("files") path: Path
) : SettingsRepository {
  val store = storeOf(file = Path(path, "settings.json"), default = AppSettings())
  override val settings: Flow<AppSettings> = store.updates.map { it ?: AppSettings() }

  override suspend fun setPresetChoices(channel: MidiChannel, presets: List<Preset>) {
    if (presets.isEmpty()) return
    store.update { it?.copy(presets = it.presets.withChoices(channel, presets)) }
  }

  override suspend fun setPresetShuffleMode(channel: MidiChannel, mode: PresetShuffleMode) {
    store.update { it?.copy(presetShuffle = it.presetShuffle.withMode(channel, mode)) }
  }

  override suspend fun setPresetVolume(preset: Preset, percent: Int) {
    store.update { it?.copy(presetVolumes = it.presetVolumes.with(preset, percent)) }
  }

  override suspend fun setPerNoteShuffleOnImportedMelodies(value: Boolean) {
    store.update {
      it?.copy(presetShuffle = it.presetShuffle.copy(perNoteOnImportedMelodies = value))
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

  override suspend fun setBackingTrackVolume(value: Int) {
    store.update { it?.copy(backingTrackVolume = value.coerceIn(0, MidiValueMax)) }
  }

  override suspend fun setBackingTrackMutesMelody(value: Boolean) {
    store.update { it?.copy(backingTrackMutesMelody = value) }
  }

  override suspend fun setInputMethod(inputMethod: InputMethod) {
    store.update { it?.copy(inputMethod = inputMethod) }
  }

  override suspend fun setTheme(theme: ThemeSettings) {
    store.update { it?.copy(theme = theme) }
  }

  override suspend fun setMidiRespectOctaves(value: Boolean) {
    store.update { it?.copy(midiRespectOctaves = value) }
  }

  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) {
    store.update { it?.copy(allowSevenAccidentalKeys = value) }
  }

  override suspend fun setLevelStatsWindow(value: Int) {
    store.update { it?.copy(levelStatsWindow = coerceLevelStatsWindow(value)) }
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

  override suspend fun setCustomNoteNamesPreset(names: List<String>) {
    store.update { settings ->
      settings?.copy(musicLabels = settings.musicLabels.copy(customNoteNamesPreset = names))
    }
  }

  override suspend fun setCustomDegreeNamesPreset(names: List<String>) {
    store.update { settings ->
      settings?.copy(musicLabels = settings.musicLabels.copy(customDegreeNamesPreset = names))
    }
  }

  override suspend fun setSoundFontPath(path: String?) {
    store.update { it?.copy(soundFontPath = path) }
  }

  private companion object {
    const val MidiValueMax = 127
  }
}
