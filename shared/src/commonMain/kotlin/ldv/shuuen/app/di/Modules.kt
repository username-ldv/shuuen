package ldv.shuuen.app.di

import ldv.shuuen.data.audio.BassMidiEngine
import ldv.shuuen.data.audio.BassMidiFilePlayer
import ldv.shuuen.core.audio.engine.MidiEngine
import ldv.shuuen.core.audio.engine.MidiFilePlayer
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

expect val platformModule: Module

val commonModule = module {
  single<BassMidiEngine>() bind MidiEngine::class
  single<BassMidiFilePlayer>() bind MidiFilePlayer::class

  includes(apiModule, dataModule, navigationModule)
}
