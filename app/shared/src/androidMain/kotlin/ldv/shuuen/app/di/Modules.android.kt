package ldv.shuuen.app.di

import android.content.Context
import kotlinx.io.files.Path
import ldv.shuuen.AndroidSoundFontProvider
import ldv.shuuen.data.audio.AndroidMidiKeyboardInput
import ldv.shuuen.data.database.AppDatabase
import ldv.shuuen.data.database.createDatabase
import ldv.shuuen.data.database.getDatabaseBuilder
import ldv.shuuen.core.audio.engine.SoundFontProvider
import ldv.shuuen.core.audio.input.MidiKeyboardInput
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

actual val platformModule: Module = module {
  single<AppDatabase> { createDatabase(getDatabaseBuilder(get())) }

  single<AndroidSoundFontProvider>() bind SoundFontProvider::class

  single<AndroidMidiKeyboardInput>() bind MidiKeyboardInput::class

  single<Path>(named("files")) {
    Path(get<Context>().filesDir.toString())
  }
}