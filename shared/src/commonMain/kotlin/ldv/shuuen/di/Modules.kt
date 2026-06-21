package ldv.shuuen.di

import ldv.shuuen.data.audio.BassMidiEngine
import ldv.shuuen.data.audio.BassMidiFilePlayer
import ldv.shuuen.domain.audio.engine.MidiEngine
import ldv.shuuen.domain.audio.engine.MidiFilePlayer
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

expect val platformModule: Module

val commonModule = module {
  single<BassMidiEngine>() bind MidiEngine::class
  single<BassMidiFilePlayer>() bind MidiFilePlayer::class

  includes(dataModule, navigationModule)
}
