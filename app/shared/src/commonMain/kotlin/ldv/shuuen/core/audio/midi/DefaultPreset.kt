package ldv.shuuen.core.audio.midi

enum class DefaultPreset(val preset: Preset) {
  Notes(Preset(bank = 0, id = 0)),
  Drone(Preset(bank = 0, id = 42)),
  Cadence(Preset(bank = 0, id = 0)),
}