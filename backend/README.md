# Shuuen Backend

Go backend for a Fiber v3 API with GORM, role-aware JWT authentication, recursive filesystem-backed catalog indexing, and file retrieval. It is designed for a single application instance today while keeping storage and catalog responsibilities separable for a future multi-instance deployment.

## Stack

- Go 1.26.5 and Fiber v3.
- GORM's Generics API with CLI-generated, type-safe field and association helpers; SQLite for fast local development and Postgres for production.
- OpenAPI 3.2 contract linted in CI with Redocly.
- JWT bearer auth with bcrypt password hashing.
- Filesystem catalog under `DATA_ROOT`, indexed into the database on startup and on demand.
- Versioned database migrations, query-oriented indexes, connection-pool configuration, and soft-delete restoration for indexed resources.
- Incremental reconciliation: unchanged files reuse cached checksums and unchanged catalog rows are not rewritten.

## Project Layout

```text
cmd/api              API entrypoint
internal/auth        Password hashing and JWT creation/parsing
internal/catalog     Recursive data-folder scanner and metadata indexing
internal/config      Environment-based configuration
internal/database    GORM connection and migrations
internal/http        Fiber server, middleware, and handlers
internal/model       GORM models
internal/query       GORM CLI-generated field helpers and shared scopes
internal/storage     Filesystem upload/download support
internal/util        Small shared helpers
```

## Quick Start

Copy the environment template:

```sh
cp .env.example .env
```

Run locally:

```sh
go mod tidy
go run ./cmd/api
```

After changing a model, regenerate the typed GORM helpers:

```sh
go generate ./internal/model
```

Or run with Postgres through Docker Compose:

```sh
docker compose up --build
```

The API listens on `http://localhost:9999` by default.

## Data Folder Catalog

`DATA_ROOT=data` by default. Every folder below it becomes a library group, recursively.

Example filesystem:

```text
data/
  my_textbook/
    .shuuen.json
    1/
      .shuuen.json
      warmup.mid
      warmup.musicxml
      warmup.shuuen.json
  random_things/
    today/
      scary/
        melody.mid
```

Example path mapping:

- `GET /api/my_textbook/1`
- `GET /api/random_things/today/scary`
- `GET /api/v1/library/path/my_textbook/1`

Each file stem becomes a melody. Files with the same stem in the same folder become variants of that melody:

```text
warmup.mid
warmup.musicxml
```

Both become variants for the `warmup` melody.

## Metadata

Folder metadata lives in `.shuuen.json`:

```json
{
  "name": "Grade 1",
  "description": "Beginner melodies",
  "tags": ["grade-1", "textbook"],
  "sort_order": 10,
  "is_public": true
}
```

Melody metadata lives beside the file as `<stem>.shuuen.json`:

```json
{
  "title": "Warmup",
  "composer": "Traditional",
  "difficulty": "easy",
  "tags": ["warmup", "midi"],
  "sort_order": 1,
  "is_public": true,
  "primary_format": "musicxml"
}
```

If metadata is missing, names are derived from folders and file names. Melodies
without an explicit `sort_order` are naturally ordered within their folder, so
numeric filename runs sort as `1`, `2`, `10` instead of `1`, `10`, `2`. An
explicit melody `sort_order` overrides that generated position. Resources are
public by default. A private group makes all of its descendant groups and
melodies private. The legacy `is_active` and `is_published` metadata keys are
still read for compatibility, but new metadata should use only `is_public`.

Malformed metadata fails the scan instead of silently falling back to public visibility.

## Authentication

Main endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/password`

Register and login with a `username` of 3-20 letters, numbers, or underscores
plus a password. Username display casing is preserved, but login and uniqueness
checks are case-insensitive.

Changing a password returns a replacement access token and immediately revokes all previously issued tokens for that account.

Send authenticated mutation requests with:

```text
Authorization: Bearer <access_token>
```

Catalog mutations require an administrator account. Ordinary registered users cannot upload, delete, change tags, or rescan the catalog.

To create the initial administrator, set these for one startup:

```text
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<8-72 byte password>
```

Remove the bootstrap password from the environment after the account exists. Re-running bootstrap never resets an existing administrator password and refuses to promote an existing ordinary account.

Registration defaults to enabled in development/test and disabled in other environments. Override it with `REGISTRATION_ENABLED`. Authentication and administrative operations have separate configurable per-IP rate limits.

For production or staging, use a `JWT_SECRET` of at least 32 characters and configure explicit `CORS_ALLOWED_ORIGINS`; wildcard CORS is rejected outside development/test.

## API Surface

The machine-readable contract is in [`openapi.yaml`](openapi.yaml).

Public catalog endpoints return only public resources:

- `GET /api/:group_path...`
- `GET /api/v1/library/path/:group_path...`
- `GET /api/v1/library/groups`
- `GET /api/v1/library/groups/:id`
- `GET /api/v1/library/tags`
- `GET /api/v1/library/tags/:id`
- `GET /api/v1/library/melodies`
- `GET /api/v1/library/melodies/:id`
- `GET /api/v1/library/melodies/:id/variants`
- `GET /api/v1/library/variants/:id`
- `GET /api/v1/library/variants/:id/download`

## Courses and Level Progressions

Every direct child of the synthetic `Library` root is also a course. A folder
without explicit course rows is exposed as a **blueprint** course:

- It has one `melodies` mode.
- MIDI files directly in the course folder appear in a synthetic `Default` tab.
- Every direct child folder is an independent progression tab.
- Deeper folders are flattened into the tab's level page. Every returned level
  carries a `sections` breadcrumb trail so the app can insert labelled dividers.
- Blueprint groups and levels use stable ids such as `library-12`. The first
  structural admin edit transparently materializes the blueprint into editable
  database rows while preserving those ids and without moving audio files.

Course navigation never returns every level definition. The lightweight reads
are:

- `GET /api/v1/courses`
- `GET /api/v1/courses/:course_id`
- `GET /api/v1/courses/:course_id/:mode`

Level payloads are fetched lazily with:

- `GET /api/v1/courses/:course_id/:mode/levels?group_id=...&limit=20&offset=0`
- `GET .../levels?ids=id-1,id-2` for up to 200 ids
- `POST .../levels/query` with `{"ids":[...]}` when a URL would be too long
- `GET .../levels/:level_id`, which additionally returns zero-based
  `navigation` metadata with the previous and next visible level ids, position,
  and total within that level's progression group

Both id-query forms preserve the caller's requested order and silently omit
unknown or non-visible ids.

Administrator mutations are granular:

- `POST /api/v1/courses` and `PUT /api/v1/courses/:course_id` create or edit
  course metadata only.
- `POST /api/v1/courses/:course_id/modes` and `PUT /api/v1/courses/:course_id/:mode` manage one
  mode at a time.
- `POST/PUT /api/v1/courses/:course_id/:mode/groups...` manage one progression tab.
- `POST/PUT/DELETE /api/v1/courses/:course_id/:mode/levels...` manage one level.
- `PUT .../:mode/position`, `PUT .../groups/:group_id/position`, and
  `PUT .../levels/:level_id/position` use only a zero-based position. The level
  position request may also include `group_id` to move the level to another tab.

Course level definitions use stable, backend-owned JSON discriminators rather
than Kotlin class names. Singles and chords use `level_config.type` of
`absolute` or `relative`; melodies use `config.type` of `random` or `midi`.
The nested structures preserve the app's scales, active pitch/degree states,
88-key note ranges, degree contexts and setup melodies, chord styles, melody
rhythm figures, weights, and answer settings. See `openapi.yaml` for the endpoint
contract and core musical schemas.

A stored MIDI reference is explicit:

```json
{
  "definition": {
    "config": {
      "type": "midi",
      "file": {
        "type": "backend",
        "melody_id": 42,
        "variant_id": 87,
        "file_name": "lesson.mid"
      },
      "use_original_velocities": true
    },
    "context": null
  }
}
```

Public MIDI levels must reference a public backend MIDI variant. A private level
may instead use `{"type":"local","path":"...","file_name":"..."}`. Local
paths are therefore never returned by anonymous course reads. Deleting or moving
a course-level record does not delete or move the referenced MIDI file.

### Generated test courses

Seed the configured database and catalog root with the public `C tonic` and
`Random tonic` test courses:

```bash
go run ./cmd/seed-c-tonic
```

`Random tonic` mirrors the six C-tonic tempo progressions using relative Major
degree states. Its tonic rotates every five questions, its questions contain
five-note sequences, and the groups cumulatively add ♯4, ♭2, ♭6, ♭3, and ♭7.

The command is idempotent. It creates one `melodies` mode containing six
progression tabs and 66 generated-melody levels (60–160 BPM in 10 BPM steps).
The tabs cumulatively add F♯, C♯, G♯, D♯, and A♯ to C major. Running the command
again resets the seed-owned rows to their canonical definitions without
removing unrelated modes or progression groups added by an administrator.

Administrator-only catalog endpoints:

- `POST /api/v1/library/rescan`
- `POST /api/v1/library/melodies/:id/variants`
- `DELETE /api/v1/library/melodies/:id`
- `PATCH /api/v1/library/variants/:id`
- `DELETE /api/v1/library/variants/:id`
- `POST/PATCH/DELETE /api/v1/library/tags`

List endpoints support `limit` and `offset`. Melody listing supports:

- `group_id`
- `group_path`
- `recursive=true`
- `tag_id`
- `tag`
- `format=midi|musicxml`
- `public=true|false` with administrator `include_private=true`
- `q`
- `sort=title|-title|path|-path|created_at|-created_at|updated_at|-updated_at|sort_order`

Group path responses include the group, direct child groups, and a bounded page of melodies. Add `?recursive=true` to include melodies from descendant folders. Melody pages use the same `limit`/`offset` parameters and include `melodies_meta`.

Administrators can inspect private resources by sending their bearer token and adding `?include_private=true`. Without that explicit scope, administrator reads have the same visibility as public reads.

## Uploading Variants

Use multipart form data to add a variant to an existing melody:

```sh
curl -X POST http://localhost:9999/api/v1/library/melodies/1/variants \
  -H "Authorization: Bearer <access_token>" \
  -F "format=midi" \
  -F "file=@warmup.mid"
```

The uploaded file is written into the melody's source folder and its variant row is created directly. Uploads do not trigger a whole-library rescan. Deletes first stage files under the hidden `.trash` directory, so database failures can roll back the filesystem change; interrupted staged deletes are reconciled on the next startup.

Allowed formats:

- MIDI: `.mid`, `.midi`
- MusicXML: `.musicxml`, `.mxl`, `.xml`

## Catalog Reconciliation and Large Libraries

The startup scan is enabled by default and can be disabled with `CATALOG_SCAN_ON_STARTUP=false` once the database is already indexed. An administrator can run reconciliation with `POST /api/v1/library/rescan`.

Reconciliation is serialized within the process, loads existing catalog identities in batches, hashes only new or changed files using size and modification time, and updates scan markers in batches. This keeps repeat scans practical for libraries with tens of thousands of files. The database remains the fast read index; the filesystem remains the current source of catalog file content.

SQLite connections use WAL mode, foreign keys, a busy timeout, and a small read-capable pool by default. Postgres and SQLite pool sizes/lifetimes are configurable. Recursive API responses are bounded and default list ordering is backed by composite indexes.

For a future multi-machine deployment, replace local file storage with shared/object storage and run reconciliation in a separately leased worker. That distributed coordination is intentionally not implemented yet.

## Migrations and Production Checklist

Migrations are versioned in the `schema_migrations` table and are safe to rerun. Existing `is_active`/`is_published` database columns are migrated to `is_public` while preserving values. Keep `AUTO_MIGRATE=true` during normal deployments so pending versioned migrations run before scanning.

Before production:

- Set `APP_ENV=production`, a strong `JWT_SECRET`, and explicit CORS origins.
- Bootstrap an administrator, then remove the bootstrap password.
- Decide whether public registration and startup scanning should be enabled.
- Back up both the database and `DATA_ROOT` together.
- Run the application behind TLS and monitor `/healthz`.

The Docker image runs as a non-root user, excludes local data/secrets from its build context, and includes a health check. Docker Compose values are development-only and must not be reused as production secrets.

## Future User-Data Sync

User-data sync remains intentionally out of scope. Timestamps and catalog tombstones are useful groundwork, but a real sync protocol still needs stable public identifiers, per-user ownership, a monotonically increasing change log, conflict rules, and a tombstone-retention policy. A practical future API could include:

- `GET /api/v1/sync/changes?since_revision=...`
- `POST /api/v1/sync/push`
