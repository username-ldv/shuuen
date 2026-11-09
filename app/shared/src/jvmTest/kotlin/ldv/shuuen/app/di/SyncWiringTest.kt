package ldv.shuuen.app.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import ldv.shuuen.core.audio.engine.SoundFontProvider
import ldv.shuuen.core.audio.input.MidiKeyboardEvent
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import ldv.shuuen.core.coreModule
import ldv.shuuen.core.database.AppDatabase
import ldv.shuuen.core.sync.DataSync
import ldv.shuuen.features.context.contextModule
import ldv.shuuen.features.training.level.levelModule
import ldv.shuuen.features.training.session.sessionModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The real modules, run by Koin: every feature's `bind Synchronizer::class` reaches DataSync. */
class SyncWiringTest {
  @Test
  fun dataSyncRunsEveryFeaturesSynchronizer() = runBlocking {
    val directory = createTempDirectory("shuuen-sync-wiring-test")
    val database =
      Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    val testPlatform = module {
      single<AppDatabase> { database }
      single<Path>(named("files")) { Path(directory.toString()) }
      single<SoundFontProvider> { NoSoundFonts }
      single<MidiKeyboardInput> { NoMidiKeyboard }
    }
    val app = koinApplication {
      modules(testPlatform, coreModule, contextModule, levelModule, sessionModule)
    }
    try {
      // Signed out, so each synchronizer fails right away with its own message.
      val report = app.koin.get<DataSync>().syncAll()

      assertEquals(
        setOf("Sign in before syncing levels.", "Sign in before syncing training sessions."),
        report.failures.map { it.message }.toSet(),
      )
    } finally {
      app.close()
      database.close()
      directory.toFile().deleteRecursively()
    }
  }

  private object NoSoundFonts : SoundFontProvider {
    override suspend fun loadSoundFont(location: String) = 0

    override suspend fun loadDefaultSoundFont() = 0
  }

  private object NoMidiKeyboard : MidiKeyboardInput {
    override val connectedDevices = MutableStateFlow(emptyList<String>())
    override val events = MutableSharedFlow<MidiKeyboardEvent>()
  }
}
