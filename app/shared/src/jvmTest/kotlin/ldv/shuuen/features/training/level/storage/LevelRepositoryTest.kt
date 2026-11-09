package ldv.shuuen.features.training.level.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ldv.shuuen.core.database.AppDatabase
import ldv.shuuen.core.music.ContextDuration
import ldv.shuuen.core.music.ContextSource
import ldv.shuuen.core.music.Degree
import ldv.shuuen.core.music.DegreeContext
import ldv.shuuen.core.music.DegreeContextNode
import ldv.shuuen.core.music.DegreeWithOctave
import ldv.shuuen.core.music.Note
import ldv.shuuen.core.music.NoteRange
import ldv.shuuen.core.music.Pitch
import ldv.shuuen.core.music.ScaleType
import ldv.shuuen.core.music.Sustain
import ldv.shuuen.features.context.ContextRepository
import ldv.shuuen.features.training.level.LevelConfig
import ldv.shuuen.features.training.level.LevelSource
import ldv.shuuen.features.training.level.ScaleConfig
import ldv.shuuen.features.training.level.SinglesLevel

/** The level storage against a real (in-memory) Room database. */
class LevelRepositoryTest {
  private val database =
    Room.inMemoryDatabaseBuilder<AppDatabase>()
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .build()
  private val repository =
    SinglesLocalLevelRepositoryImpl(
      database.singlesLevelDao(),
      ContextRepository(database.contextDao()),
    )

  @AfterTest
  fun closeDatabase() {
    database.close()
  }

  @Test
  fun aSavedLevelComesBackWithItsContext() = runBlocking {
    val level = level(id = "level-1", context = context("context-1"))

    repository.upsertLevel(level)

    assertEquals(level, repository.getLevel("level-1"))
    assertEquals(listOf(level), repository.observeLevels().first())
    assertNull(repository.getLevel("missing"))
  }

  @Test
  fun observedLevelsFollowEveryChange() = runBlocking {
    val first = level(id = "level-1", context = context("context-1"))
    val second = level(id = "level-2", context = null)

    repository.upsertLevel(first)
    repository.upsertLevel(second)
    assertEquals(setOf(first, second), repository.observeLevels().first().toSet())

    val renamed = first.copy(name = "Renamed")
    repository.upsertLevel(renamed)
    repository.deleteLevel("level-2")
    assertEquals(listOf(renamed), repository.observeLevels().first())
  }

  private fun context(id: String) =
    DegreeContext(
      id = id,
      source = ContextSource.UserLocal,
      nodes =
        listOf(
          DegreeContextNode(
            firstDegree = DegreeWithOctave(Degree.D1, 3),
            extraDegrees = listOf(Degree.D3, Degree.D5),
            sustain = Sustain.Endless,
            duration = ContextDuration.Endless,
            setupMelody = null,
          )
        ),
      name = "Cadence",
    )

  private fun level(id: String, context: DegreeContext?) =
    SinglesLevel(
      id = id,
      name = "Level $id",
      levelConfig =
        LevelConfig.Singles.Absolute(
          scales =
            listOf(
              ScaleConfig.AbsoluteScaleConfig(
                root = Pitch.C,
                scaleType = ScaleType.Major,
                pitchStates =
                  Pitch.entries.map {
                    ScaleConfig.ScaleItemState.ScalePitchState(it, it == Pitch.C)
                  },
              )
            )
        ),
      context = context,
      source = LevelSource.User,
      questionsNumber = 20,
      range = NoteRange(Note(48), Note(72)),
    )
}
