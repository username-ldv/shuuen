package ldv.shuuen.app.di

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ldv.shuuen.DesktopSoundFontProvider
import ldv.shuuen.data.database.AppDatabase
import ldv.shuuen.data.database.createDatabase
import ldv.shuuen.data.database.getDatabaseBuilder
import ldv.shuuen.core.audio.engine.SoundFontProvider
import net.harawata.appdirs.AppDirsFactory
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

actual val platformModule: Module = module {
  single<AppDatabase> { createDatabase(getDatabaseBuilder(get(named("files")))) }

  single<DesktopSoundFontProvider>() bind SoundFontProvider::class

  single<Path>(named("files")) {
    val path = Path(AppDirsFactory.getInstance().getUserDataDir("Shuuen", null, null))
    with(SystemFileSystem) { if (!exists(path)) createDirectories(path) }
    path
  }
}