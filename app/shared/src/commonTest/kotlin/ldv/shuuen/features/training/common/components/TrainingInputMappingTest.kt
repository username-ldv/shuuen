package ldv.shuuen.features.training.common.components

import kotlin.test.Test
import kotlin.test.assertEquals
import ldv.shuuen.core.music.MusicLabelDefaults
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleAccidentalType
import ldv.shuuen.core.settings.InputMode
import ldv.shuuen.core.settings.MusicLabelSettings

class TrainingInputMappingTest {
  @Test
  fun relativePitchLabelUsesDegreeOffsetFromRootAndCustomNames() {
    val labels =
      MusicLabelSettings(
        degreeNames = List(MusicLabelDefaults.DegreeNames.size) { index -> "d$index" },
      )

    assertEquals(
      "d2",
      inputLabelForPitch(
        pitch = Pitch.A,
        mode = InputMode.Relative,
        root = Pitch.G,
        accidentalType = ScaleAccidentalType.Sharps,
        musicLabels = labels,
      ),
    )
  }

  @Test
  fun absolutePitchLabelUsesKeyAwareFlatCustomName() {
    val labels =
      MusicLabelSettings(
        noteNames = List(MusicLabelDefaults.NoteNames.size) { index -> "n$index" },
      )

    assertEquals(
      "n20",
      inputLabelForPitch(
        pitch = Pitch.ASharp,
        mode = InputMode.Absolute,
        root = Pitch.F,
        accidentalType = ScaleAccidentalType.Flats,
        musicLabels = labels,
      ),
    )
  }

  @Test
  fun absolutePitchLabelUsesRootlessCircleSpellingBeforeRootIsKnown() {
    val labels =
      MusicLabelSettings(
        noteNames = List(MusicLabelDefaults.NoteNames.size) { index -> "n$index" },
      )

    assertEquals(
      "n15",
      inputLabelForPitch(
        pitch = Pitch.CSharp,
        mode = InputMode.Absolute,
        root = null,
        accidentalType = null,
        musicLabels = labels,
      ),
    )
  }
}
