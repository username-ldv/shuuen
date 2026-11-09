package ldv.shuuen.app.di

import ldv.shuuen.core.coreModule
import ldv.shuuen.features.auth.authModule
import ldv.shuuen.features.context.contextModule
import ldv.shuuen.features.free_play.freePlayModule
import ldv.shuuen.features.main.mainMenuModule
import ldv.shuuen.features.pitch_slide.pitchSlideModule
import ldv.shuuen.features.settings.settingsModule
import ldv.shuuen.features.training.chords.chordsModule
import ldv.shuuen.features.training.course.courseModule
import ldv.shuuen.features.training.level.levelModule
import ldv.shuuen.features.training.level_end.levelEndModule
import ldv.shuuen.features.training.melodies.melodiesModule
import ldv.shuuen.features.training.session.sessionModule
import ldv.shuuen.features.training.single.singlesModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform implementations: the database file, sound font, MIDI input and the files directory. */
expect val platformModule: Module

/** Every module of the app. Each feature registers its own bindings and navigation entries. */
val appModule = module {
  includes(
    platformModule,
    coreModule,
    contextModule,
    levelModule,
    sessionModule,
    courseModule,
    singlesModule,
    melodiesModule,
    chordsModule,
    levelEndModule,
    mainMenuModule,
    freePlayModule,
    pitchSlideModule,
    settingsModule,
    authModule,
  )
}
