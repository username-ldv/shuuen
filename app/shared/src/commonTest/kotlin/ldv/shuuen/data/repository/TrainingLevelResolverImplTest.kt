package ldv.shuuen.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ldv.shuuen.core.audio.midi.MidiChannel
import ldv.shuuen.core.audio.midi.Preset
import ldv.shuuen.core.audio.midi.PresetCutoffScope
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.result.ResponseState
import ldv.shuuen.core.settings.AppSettings
import ldv.shuuen.core.settings.CourseOverrides
import ldv.shuuen.core.settings.InputMethod
import ldv.shuuen.core.settings.MidiLevelOptions
import ldv.shuuen.core.settings.PresetShuffleMode
import ldv.shuuen.core.settings.SettingsRepository
import ldv.shuuen.core.settings.ThemeSettings
import ldv.shuuen.features.training.chords.domain.ChordsLevel
import ldv.shuuen.features.training.chords.domain.ChordsLocalLevelRepository
import ldv.shuuen.features.training.common.TrainingFlow
import ldv.shuuen.features.training.course.domain.CourseLevelItem
import ldv.shuuen.features.training.course.domain.CourseLevelPage
import ldv.shuuen.features.training.course.domain.CourseLevelQuery
import ldv.shuuen.features.training.course.domain.CourseMode
import ldv.shuuen.features.training.course.domain.CoursePage
import ldv.shuuen.features.training.course.domain.CourseRepository
import ldv.shuuen.features.training.course.domain.CourseSummary
import ldv.shuuen.features.training.course.domain.LevelReference
import ldv.shuuen.features.training.course.domain.PlayableTrainingLevel
import ldv.shuuen.features.training.domain.LevelConfig
import ldv.shuuen.features.training.domain.LevelSource
import ldv.shuuen.features.training.domain.ScaleConfig
import ldv.shuuen.features.training.melodies.domain.MelodiesLevel
import ldv.shuuen.features.training.melodies.domain.MelodiesLocalLevelRepository
import ldv.shuuen.features.training.single.domain.SinglesLevel
import ldv.shuuen.features.training.single.domain.SinglesLocalLevelRepository

class TrainingLevelResolverImplTest {
  @Test
  fun routesLocalAndRemoteReferencesWithoutChangingLocalIds() = runTest {
    val local = melody("local-id")
    val remoteReference = LevelReference.Remote(5, TrainingFlow.Melodies, "remote-id")
    val remote = melody(remoteReference.encoded)
    val localRepository = FakeMelodiesLocalRepository(local)
    val resolver =
      resolver(
        melodiesRepository = localRepository,
        courseLevels = mapOf(remoteReference to PlayableTrainingLevel.Melodies(remote)),
      )

    assertEquals("local-id", resolver.resolveMelodies("local-id").id)
    assertEquals("local-id", localRepository.requestedId)
    assertEquals(remoteReference.encoded, resolver.resolveMelodies(remoteReference.encoded).id)
    assertEquals("local-id", localRepository.requestedId)
  }

  @Test
  fun courseLevelsPlayWithTheirCoursesTuneOverride() = runTest {
    val melodyReference = LevelReference.Remote(5, TrainingFlow.Melodies, "melody")
    val singleReference = LevelReference.Remote(5, TrainingFlow.Singles, "single")
    val resolver =
      resolver(
        courseLevels =
          mapOf(
            melodyReference to
              PlayableTrainingLevel.Melodies(melody(melodyReference.encoded, tuneCents = 10)),
            singleReference to
              PlayableTrainingLevel.Singles(single(singleReference.encoded, tuneCents = 10)),
          ),
        settings = AppSettings(courseOverrides = mapOf(5L to CourseOverrides(tuneInconsistencyCents = 40))),
      )

    val melodyConfig = resolver.resolveMelodies(melodyReference.encoded).config
    assertEquals(40, (melodyConfig as LevelConfig.Melodies.Random).tuneInconsistencyCents)
    val single = resolver.resolveSingles(singleReference.encoded)
    assertEquals(40, single.levelConfig.tuneInconsistencyCents)
  }

  @Test
  fun anOverrideOfZeroTunesEveryCourseLevelPerfectly() = runTest {
    val reference = LevelReference.Remote(5, TrainingFlow.Singles, "single")
    val resolver =
      resolver(
        courseLevels =
          mapOf(reference to PlayableTrainingLevel.Singles(single(reference.encoded, tuneCents = 25))),
        settings = AppSettings(courseOverrides = mapOf(5L to CourseOverrides(tuneInconsistencyCents = 0))),
      )

    assertEquals(0, resolver.resolveSingles(reference.encoded).levelConfig.tuneInconsistencyCents)
  }

  @Test
  fun localLevelsAndOtherCoursesKeepTheirOwnTune() = runTest {
    val otherCourseReference = LevelReference.Remote(6, TrainingFlow.Melodies, "melody")
    val resolver =
      resolver(
        melodiesRepository = FakeMelodiesLocalRepository(melody("local-id", tuneCents = 10)),
        courseLevels =
          mapOf(
            otherCourseReference to
              PlayableTrainingLevel.Melodies(melody(otherCourseReference.encoded, tuneCents = 15)),
          ),
        settings = AppSettings(courseOverrides = mapOf(5L to CourseOverrides(tuneInconsistencyCents = 40))),
      )

    val localConfig = resolver.resolveMelodies("local-id").config
    assertEquals(10, (localConfig as LevelConfig.Melodies.Random).tuneInconsistencyCents)
    val otherConfig = resolver.resolveMelodies(otherCourseReference.encoded).config
    assertEquals(15, (otherConfig as LevelConfig.Melodies.Random).tuneInconsistencyCents)
  }
}

private fun resolver(
  melodiesRepository: MelodiesLocalLevelRepository = FakeMelodiesLocalRepository(melody("local-id")),
  courseLevels: Map<LevelReference.Remote, PlayableTrainingLevel>,
  settings: AppSettings = AppSettings(),
) =
  TrainingLevelResolverImpl(
    singlesRepository = EmptySinglesRepository,
    melodiesRepository = melodiesRepository,
    chordsRepository = EmptyChordsRepository,
    courseRepository = ResolverCourseRepository(courseLevels),
    settingsRepository = FixedSettingsRepository(settings),
  )

private fun melody(id: String, tuneCents: Int = 0) =
  MelodiesLevel(
    id = id,
    name = id,
    config =
      LevelConfig.Melodies.Random(
        scaleConfig =
          ScaleConfig.AbsoluteScaleConfig(
            Pitch.C,
            ScaleType.Major,
            listOf(ScaleConfig.ScaleItemState.ScalePitchState(Pitch.C, true)),
          ),
        questionsNumber = 1,
        notesPerSequence = 1,
        tempo = 60,
        range = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 4)),
        tuneInconsistencyCents = tuneCents,
      ),
    context = null,
    source = LevelSource.User,
  )

private fun single(id: String, tuneCents: Int) =
  SinglesLevel(
    id = id,
    name = id,
    levelConfig =
      LevelConfig.Singles.Absolute(
        scales =
          listOf(
            ScaleConfig.AbsoluteScaleConfig(
              Pitch.C,
              ScaleType.Major,
              listOf(ScaleConfig.ScaleItemState.ScalePitchState(Pitch.C, true)),
            )
          ),
        tuneInconsistencyCents = tuneCents,
      ),
    context = null,
    source = LevelSource.User,
    questionsNumber = 1,
    range = NoteRange(Note(Pitch.C, 4), Note(Pitch.C, 4)),
  )

private class FakeMelodiesLocalRepository(private val level: MelodiesLevel) :
  MelodiesLocalLevelRepository {
  var requestedId: String? = null
  override fun getLevels(): Flow<ResponseState<List<MelodiesLevel>>> =
    flowOf(ResponseState.Success(listOf(level)))
  override fun getLevelById(id: String): Flow<ResponseState<MelodiesLevel>> {
    requestedId = id
    return flowOf(ResponseState.Success(level))
  }
  override suspend fun upsertLevel(level: MelodiesLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private object EmptySinglesRepository : SinglesLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<SinglesLevel>>> = flowOf(ResponseState.Success(emptyList()))
  override fun getLevelById(id: String): Flow<ResponseState<SinglesLevel>> = error("not used")
  override suspend fun upsertLevel(level: SinglesLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private object EmptyChordsRepository : ChordsLocalLevelRepository {
  override fun getLevels(): Flow<ResponseState<List<ChordsLevel>>> = flowOf(ResponseState.Success(emptyList()))
  override fun getLevelById(id: String): Flow<ResponseState<ChordsLevel>> = error("not used")
  override suspend fun upsertLevel(level: ChordsLevel) = Unit
  override suspend fun deleteLevel(id: String) = Unit
}

private class ResolverCourseRepository(
  private val levels: Map<LevelReference.Remote, PlayableTrainingLevel>,
) : CourseRepository {
  override suspend fun listCourses(limit: Int, offset: Int): CoursePage = error("not used")
  override suspend fun getCourse(courseId: Long): CourseSummary = error("not used")
  override suspend fun getCourseMode(courseId: Long, mode: TrainingFlow): CourseMode = error("not used")
  override suspend fun getLevels(
    courseId: Long,
    mode: TrainingFlow,
    groupId: String,
    limit: Int,
    offset: Int,
  ): CourseLevelPage = error("not used")
  override suspend fun getLevel(reference: LevelReference.Remote): CourseLevelItem =
    CourseLevelItem(
      reference = reference,
      playable = levels.getValue(reference),
      progressionGroupId = "group",
      sortOrder = 0,
      sections = emptyList(),
      sourceCourseId = reference.courseId,
      mode = reference.mode,
    )
  override suspend fun queryLevels(
    courseId: Long,
    mode: TrainingFlow,
    levelIds: List<String>,
  ): CourseLevelQuery = error("not used")
}

private class FixedSettingsRepository(initial: AppSettings) : SettingsRepository {
  override val settings: Flow<AppSettings> = MutableStateFlow(initial)
  override suspend fun setBackendUrl(url: String?) = Unit
  override suspend fun setSoundFontPath(path: String?) = Unit
  override suspend fun setPresetChoices(channel: MidiChannel, presets: List<Preset>) = Unit
  override suspend fun setPresetShuffleMode(channel: MidiChannel, mode: PresetShuffleMode) = Unit
  override suspend fun setPresetVolume(preset: Preset, percent: Int) = Unit
  override suspend fun setPresetCutoff(preset: Preset, cutoff: Int) = Unit
  override suspend fun setPresetCutoffScope(preset: Preset, scope: PresetCutoffScope) = Unit
  override suspend fun setPerNoteShuffleOnImportedMelodies(value: Boolean) = Unit
  override suspend fun setVolume(channel: MidiChannel, value: Int) = Unit
  override suspend fun setMelodyOriginalVolumeBoost(value: Int) = Unit
  override suspend fun setBackingTrackVolume(value: Int) = Unit
  override suspend fun setBackingTrackMutesMelody(value: Boolean) = Unit
  override suspend fun setMidiLevelOptions(options: MidiLevelOptions) = Unit
  override suspend fun setCourseTuneInconsistency(courseId: Long, cents: Int?) = Unit
  override suspend fun setInputMethod(inputMethod: InputMethod) = Unit
  override suspend fun setTheme(theme: ThemeSettings) = Unit
  override suspend fun setMidiRespectOctaves(value: Boolean) = Unit
  override suspend fun setAllowSevenAccidentalKeys(value: Boolean) = Unit
  override suspend fun setLevelStatsWindow(value: Int) = Unit
  override suspend fun setNoteNames(names: List<String>) = Unit
  override suspend fun setDegreeNames(names: List<String>) = Unit
  override suspend fun setCustomNoteNamesPreset(names: List<String>) = Unit
  override suspend fun setCustomDegreeNamesPreset(names: List<String>) = Unit
}
