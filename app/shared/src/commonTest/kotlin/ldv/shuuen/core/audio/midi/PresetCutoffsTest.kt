package ldv.shuuen.core.audio.midi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresetCutoffsTest {
  private val piano = Preset(bank = 0, id = 0)
  private val violin = Preset(bank = 0, id = 40)

  @Test
  fun absentPresetHasNoOverride() {
    assertNull(PresetCutoffs().forPreset(piano))
  }

  @Test
  fun valuesBelongToIndividualPresets() {
    val cutoffs = PresetCutoffs().with(piano, 96).with(violin, 72)

    assertEquals(96, cutoffs.forPreset(piano))
    assertEquals(72, cutoffs.forPreset(violin))
  }

  @Test
  fun neutralValueRemovesOverride() {
    val cutoffs = PresetCutoffs().with(piano, 96).with(piano, NeutralPresetCutoff)

    assertNull(cutoffs.forPreset(piano))
    assertEquals(emptyMap(), cutoffs.values)
  }

  @Test
  fun storedOverrideIsLimitedToMidiRange() {
    assertEquals(MaximumPresetCutoff, PresetCutoffs().with(piano, 999).forPreset(piano))
  }

  @Test
  fun defaultScopeOnlyAppliesToOriginalVelocityMelodies() {
    val cutoffs = PresetCutoffs().with(piano, 96)

    assertEquals(DefaultPresetCutoffScope, cutoffs.scopeForPreset(piano))
    assertNull(cutoffs.effectiveForPreset(piano, originalVelocityMelody = false))
    assertEquals(96, cutoffs.effectiveForPreset(piano, originalVelocityMelody = true))
  }

  @Test
  fun allPlaybackScopeAppliesOutsideMidiMelodies() {
    val cutoffs =
      PresetCutoffs()
        .with(piano, 96)
        .withScope(piano, PresetCutoffScope.AllPlayback)

    assertEquals(96, cutoffs.effectiveForPreset(piano, originalVelocityMelody = false))
    assertEquals(96, cutoffs.effectiveForPreset(piano, originalVelocityMelody = true))
  }

  @Test
  fun defaultScopeIsStoredAsAbsence() {
    val cutoffs =
      PresetCutoffs()
        .withScope(piano, PresetCutoffScope.AllPlayback)
        .withScope(piano, DefaultPresetCutoffScope)

    assertEquals(emptyMap(), cutoffs.scopes)
  }
}
