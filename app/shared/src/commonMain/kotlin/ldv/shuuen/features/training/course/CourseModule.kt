package ldv.shuuen.features.training.course

import ldv.shuuen.features.training.level.midi.MidiContentResolver
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** The backend's courses, and resolving a level reference to a playable level. */
val courseModule = module {
  single<CourseApi>()
  single<CourseDefinitionMapper>()
  single<CourseRepositoryImpl>() bind CourseRepository::class
  single<MidiContentResolverImpl>() bind MidiContentResolver::class
  single<TrainingLevelResolverImpl>() bind TrainingLevelResolver::class
}
