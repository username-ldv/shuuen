# Shuuen Backend

Production-minded Go backend foundation for a Fiber v3 API with GORM, JWT auth, recursive filesystem-backed catalog indexing, and file retrieval.

## Stack

- Go 1.25+ and Fiber v3.
- GORM with SQLite for fast local development and Postgres for production.
- JWT bearer auth with bcrypt password hashing.
- Filesystem catalog under `DATA_ROOT`, indexed into the database on startup and on demand.
- Soft deletes and timestamps on indexed resources, leaving a straightforward path for future user-data sync.

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

The API listens on `http://localhost:8080` by default.

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
  "is_active": true
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
  "is_published": true,
  "primary_format": "musicxml"
}
```

If metadata is missing, names are derived from folders and file names. Tags from metadata are created automatically during scans.

## Authentication

Main endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

Send authenticated mutation requests with:

```text
Authorization: Bearer <access_token>
```

For production, set `APP_ENV=production` and use a strong `JWT_SECRET` of at least 32 characters.

## API Surface

Public catalog endpoints:

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

Authenticated catalog endpoints:

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
- `published=true|false`
- `q`
- `sort=title|-title|path|-path|created_at|-created_at|updated_at|-updated_at|sort_order`

Group path responses include the group, direct child groups, and melodies in that group. Add `?recursive=true` to include melodies from descendant folders.

## Uploading Variants

Use multipart form data to add a variant to an existing melody:

```sh
curl -X POST http://localhost:8080/api/v1/library/melodies/1/variants \
  -H "Authorization: Bearer <access_token>" \
  -F "format=midi" \
  -F "file=@warmup.mid"
```

The uploaded file is written into the melody's source folder and the catalog is rescanned.

Allowed formats:

- MIDI: `.mid`, `.midi`
- MusicXML: `.musicxml`, `.mxl`, `.xml`

## Future Sync Direction

The current foundation leaves user-data sync out of the first pass, but indexed models already include `created_at`, `updated_at`, and soft-delete state. A practical next step is adding per-user syncable tables with monotonically increasing revision numbers and endpoints such as:

- `GET /api/v1/sync/changes?since_revision=...`
- `POST /api/v1/sync/push`
