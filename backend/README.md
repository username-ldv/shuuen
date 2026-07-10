# Shuuen Backend

Go backend for a Fiber v3 API with GORM, role-aware JWT authentication, recursive filesystem-backed catalog indexing, and file retrieval. It is designed for a single application instance today while keeping storage and catalog responsibilities separable for a future multi-instance deployment.

## Stack

- Go 1.25 language baseline with the security-patched Go 1.26.5 toolchain, and Fiber v3.
- GORM with SQLite for fast local development and Postgres for production.
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

If metadata is missing, names are derived from folders and file names. Resources are public by default. A private group makes all of its descendant groups and melodies private. The legacy `is_active` and `is_published` metadata keys are still read for compatibility, but new metadata should use only `is_public`.

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
