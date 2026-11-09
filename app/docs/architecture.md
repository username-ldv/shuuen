# App architecture

How the app's code is organized, and the rules that keep it that way. If the code and this file disagree, fix one of them.

All code lives in the `shared` Kotlin Multiplatform module (`commonMain`, with `androidMain` and `jvmMain` for platform `actual`s in the same packages). `androidApp` and `desktopApp` only start it.

## Principles

- **Package by feature.** Everything one feature needs lives in its folder: models, API classes and DTOs, DAOs and entities, the repository, the Koin module with its navigation entries, and the screens. A change to a feature touches one place.
- **Dependencies go one way:** `app -> features -> core`.
- **Add abstractions only when they're used.** An interface needs a second implementation or a test fake. A mapper class needs real logic; otherwise use an extension function next to the DTO or entity. No pass-through layers, no `domain/` or use-case layer.
- **Offline first.** Screens read the local database and settings. The backend adds courses and an optional account with manual sync; everything else works without it.

## Layout

```
ldv.shuuen
├── app/                 App, NavigationRoot, di/AppModule (includes every module, platformModule)
├── core/                shared infrastructure, no feature logic
│   ├── CoreModule.kt    Koin bindings of everything in core
│   ├── audio/           engine and input interfaces, midi/ presets and volumes,
│   │                    bass/ the BASS implementations
│   ├── auth/            AuthRepository(+Impl), AuthApi, the client's bearer handling
│   ├── database/        AppDatabase, Migrations, RoomJson, GeneralTypeConverter, builders
│   ├── music/           notes, scales, contexts, melody and chord generators
│   ├── navigation/      AppRoute, AppNavigator, result/ (results passed between screens)
│   ├── network/         ApiConfig, the client setup (configureApiClient, BackendUrl),
│   │                    shared DTOs, BackendStatusMonitor
│   ├── settings/        SettingsRepository + KStoreSettingsRepository and the settings models
│   ├── sync/            Synchronizer, SyncResult, DataSync
│   ├── ui/              theme, components, ScreenState
│   └── util/
└── features/
    ├── auth/  context/  free_play/  main/  pitch_slide/  settings/
    └── training/
        ├── level/       the level models of every flow, their storage, definition codec and sync
        ├── session/     finished training sessions: history, statistics, storage and sync
        ├── course/      backend courses (API, mapper, repository), LevelReference,
        │                TrainingLevelResolver, presentation/ (course browsing UI)
        ├── common/      training UI pieces and play helpers shared by the flows
        ├── single/  melodies/  chords/   level_select/, setup/, play/: screens and flow logic
        └── level_end/   the level-complete screen shared by the flows
```

## Dependency rules

```
app ──> features ──> core
          │  ▲
          └──┘  one way, no cycles
```

- `core` never imports `features` or `app`, with one exception: `core/database/AppDatabase.kt` lists every feature's entities and DAOs, because Room needs one central database.
- A feature may use another feature's public pieces: its repository, its model, composables meant for reuse. It never uses another feature's DAO, API class or DTOs. Two cases are allowed on purpose:
  - Level entities reference `ContextDbEntity` for their foreign key and their `@Relation`, since Room needs the entity class for both.
  - `AppDatabase` (above).
- Inside `training`, the graph is: `level` ← `session`, `course`; `common` → `level`, `session`; the flows (`single`, `melodies`, `chords`) → `level`, `session`, `course`, `common`, `level_end`; `level_end` → `level`, `session`, `course`, `common`. `main` uses `course`, `session` and `level`.
- `app` may import anything. It only wires things together.

## Anatomy of a feature

Data and DI live in the feature's root folder; screens and ViewModels live in screen folders (`level_select/`, `play/`, ...) or next to them in small features. Example, `features/training/session/`:

| File | Contents |
|---|---|
| `TrainingSession.kt` | The model. |
| `TrainingSessionDbEntity.kt` | The Room entity, with its column type converters. |
| `TrainingSessionDao.kt` | The Room DAO. |
| `TrainingSessionRepository.kt` (+`Impl`) | What the rest of the app uses. The interface exists because ViewModel tests fake it. |
| `TrainingSessionSyncApi.kt`, `TrainingSessionSyncDtos.kt` | The HTTP call and the backend's JSON. |
| `TrainingSessionSynchronizer.kt` | Its `Synchronizer`. |
| `SessionModule.kt` | Koin bindings (and navigation entries, in features with screens). |

Naming:
- Name the model after the thing (`SinglesLevel`, `TrainingSession`).
- `XDto` is the backend's shape; `XDbEntity` is a stored row.
- `-Impl` is fine where an interface stays. Implementations built on something specific may say so (`KStoreSettingsRepository`, `KtorBackendStatusMonitor`, `BassMidiEngine`).

## Data

- **One repository per kind of data.** It talks to its DAO and API class directly; there is no data-source layer.
- **Repository functions either `suspend` and throw, or return a `Flow` from Room.** They never emit loading or error states. Lists are observed (`observeLevels()`), so screens update by themselves after an edit, a delete or a sync. Single items are `suspend` reads that return null when missing.
- **Loading and errors belong to the ViewModel's UI state**: `core/ui/ScreenState` (`asScreenState()` for flows, `loadScreenState { }` for one-shot loads) or a screen-specific state class.
- **Mapping is extension functions** next to the entity or DTO (`SinglesLevel.toDbEntity()`, `SinglesLevelWithContext.toLevel()`). Mapper classes only where the conversion is real logic worth testing on its own: `LevelDefinitionCodec`/`LevelDefinitionDecoder` (the level definition schema, shared by courses and sync) and `CourseDefinitionMapper` (the course envelope around it).
- **Interfaces stay where tests fake them**: every repository interface has a fake or a second implementation. `ContextRepository` has neither, so it is a plain class.

## Persistence

**Room.** `AppDatabase` lists the entities and keeps only the general type converters (`GeneralTypeConverter`). Feature-specific converters sit on their entity (`@ColumnTypeConverters(SinglesLevelTypeConverter::class)`). A feature binds its own DAO: `single<SinglesLevelDao> { get<AppDatabase>().singlesLevelDao() }`.

Every schema change needs a migration:
1. Bump `version` in `AppDatabase`. The build exports the new schema to `shared/schemas/ldv.shuuen.core.database.AppDatabase/`; commit it.
2. Add an `AutoMigration` to the `@Database` annotation if Room can derive it from the exported schemas, otherwise a `Migration` in `core/database/Migrations.kt`.
3. For a manual migration, write the SQL that turns the old tables into what Room now expects. Room's expected `CREATE TABLE` statements are in the generated `shared/build/generated/ksp/jvm/jvmMain/kotlin/ldv/shuuen/core/database/AppDatabase_Impl.kt` (`createAllTables`).

Room checks the result when the database opens, so a wrong migration fails at once instead of corrupting data. Never use `fallbackToDestructiveMigration()`: the database holds the user's own levels, contexts and training history. Only schema versions 1–7, which existed in development builds before the first release, are rebuilt empty (`UnmigratedVersions`).

**JSON columns.** Configs, contexts and question results are stored as JSON (`RoomJson`). Polymorphic values carry their serial name as `"type"`, which defaults to the class's fully qualified name. **Moving or renaming such a class breaks every saved row.** Keep the stored name with `@SerialName`: `LevelConfig` and `ScaleConfig` keep the names of their original package, `features.training.domain` (`StoredLevelConfigTest` pins them with real rows). Changing a stored name needs a migration that rewrites the JSON.

**KStore files.** Persisted state is split by owner, one file each:
- `settings.json`: `KStoreSettingsRepository`
- `session.json`: `AuthRepositoryImpl`
- `level-sync.json`: `LevelSynchronizer`
- `training-session-sync.json`: `TrainingSessionSynchronizer`

## Network and auth

- One `HttpClient` for the backend, built in `CoreModule` from `platformHttpClient` (Android engine on Android, CIO on desktop) and `configureApiClient`:
  - Relative URLs (`client.get("api/v1/courses")`) resolve against `ApiConfig`'s backend. The `BackendUrl` plugin does this instead of `defaultRequest` because it waits for the settings to load; until then a request would go to the platform default.
  - JSON goes through `ApiJson` (snake case, backend-owned `type` discriminators; see "Courses and Level Progressions" in `backend/README.md`).
  - A non-2xx response throws. `apiErrorMessage()` reads the backend's error envelope.
- Auth uses Ktor's `Auth` plugin with a bearer provider (`core/auth/BearerAuth.kt`):
  - The token is read from `AuthRepository.session` on every request (`cacheTokens = false`), so sign-in, sign-out and renewals take effect at once.
  - Only requests to the session's own backend carry it: a token belongs to the backend that issued it.
  - A 401 calls `AuthRepository.renewSession()` once and retries; a rejected renewal signs the user out, an unreachable backend keeps the session.
  - Login and refresh bypass the plugin (`bypassBearerAuth()`).
  - The client reads the repository lazily, because `AuthRepositoryImpl` needs `AuthApi`, which needs the client.
- Each feature has its own `XApi` class taking the `HttpClient`. Test them with Ktor's `MockEngine` and `configureApiClient`, not by faking the class.
- `backend/openapi.yaml` is the contract; when it changes, update the `XApi` classes and DTOs.

## Sync

- Sync is manual (the main menu's sync button) and needs a signed-in session.
- A kind of data that syncs has a `Synchronizer` bound with `bind Synchronizer::class` (`LevelSynchronizer` in `training/level`, `TrainingSessionSynchronizer` in `training/session`). `DataSync` receives every binding (`List<Synchronizer>`), so adding one never touches it.
- `sync()` throws when it fails, preferably a `SyncException` with a message for the user. `DataSync.syncAll()` runs every synchronizer even if one fails, sums the results and reports the failures.
- Background sync on Android would need WorkManager and a scheduler; MMA's `SyncWorker`/`SyncScheduler` are the model if it ever comes.

## Navigation

- Routes are central in `core/navigation/AppRoute.kt`; `AppNavigator` and `LocalAppNavigator` live next to them. Screens pass results back through `core/navigation/result` (`NavResultStore`, keyed by `ContextRecipient` and friends).
- Each feature registers the entries for its own screens in its module: `navigation<AppRoute.SinglesPlay> { ... }`. The level-complete screen is registered by each flow; its ViewModel by `levelEndModule`.
- Entries get the navigator from `LocalAppNavigator` and hand screens plain callbacks. Screens never navigate by themselves.

## Dependency injection (Koin)

- Bindings use the Koin compiler plugin's DSL (`single<T>()`, `viewModel<T>()`), which wires constructor parameters itself. A `List<T>` parameter receives every binding of `T`. Parameters with default values are skipped (the plugin's `skipDefaultValues`).
- The plugin checks the whole graph at compile time. Parameters passed with `parametersOf(...)` must be marked `@InjectedParam`.
- Core bindings live in `core/CoreModule.kt`, platform bindings in `app/di` (`platformModule`), and each feature has one `XModule.kt`. `app/di/AppModule.kt` includes them all.

## Adding a feature

1. Create `features/<name>/`. Add the model, `XApi` + DTOs, DAO + entity and repository as needed, with screens and ViewModels in screen folders.
2. Add routes to `core/navigation/AppRoute.kt`.
3. Write `XModule.kt` with the bindings and navigation entries, then include it in `appModule`.
4. If it stores data: add the entity to `AppDatabase`, bump the version and add a migration. Give stored polymorphic JSON types explicit `@SerialName`s.
5. If it syncs: write a `Synchronizer` and `bind Synchronizer::class`.

## Tests

- Test packages mirror the main packages.
- Repositories and synchronizers are tested with fake DAOs or with an in-memory Room database in `jvmTest` (`Room.inMemoryDatabaseBuilder`, see `LevelRepositoryTest`); API classes with `MockEngine`.
- ViewModels are tested against fake repositories; the fakes are the reason those interfaces exist.

## Later

- **Gradle modules** (`:core:*`, `:feature:*`) once build times call for them. The package layout already matches, so the split is mostly moving folders. `AppDatabase` would then move to `:app`.
- **Enforcing the dependency rules** with an architecture test (e.g. Konsist): `core` must not import `features`, and features must not form cycles.
