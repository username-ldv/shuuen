# Shuuen monorepo

Three independent projects share this repository. Each has its own toolchain and
builds on its own, so run commands from the project's folder, not the repo root.

| Folder | What | Verify with (from that folder) |
| --- | --- | --- |
| `app/` | Kotlin Multiplatform app (Android + desktop), Gradle, JDK 21 | `./gradlew :shared:compileKotlinJvm` (fast), `./gradlew :shared:jvmTest` |
| `backend/` | Go API (Fiber v3, GORM) | `go test ./...` and `go vet ./...` |
| `web/` | SvelteKit site, Bun | `bun run check` and `bun run build` |

## Cross-project contract

- `backend/openapi.yaml` is the API contract. When an endpoint or payload changes,
  update it in the same change and check both clients: the app's Ktor client in
  `app/shared/src/*/kotlin/ldv/shuuen/data/remote/`, and the web's server helpers
  in `web/src/lib/server/`.
- Level definitions use backend-owned JSON discriminators (`level_config.type`,
  `config.type`), never Kotlin class names. See "Courses and Level Progressions"
  in `backend/README.md`.
- Ports: backend `:9999`; web dev `:5173`, whose Vite proxy forwards `/api` to
  `:9999`; web production `:3000`.
- After changing a GORM model, run `go generate ./internal/model` in `backend/`.
  CI fails when `internal/query/models.go` is stale.

## Per-project guidance

- `web/`: follow `web/docs/CONTRIBUTING.md` and the shadcn-svelte skill in
  `web/.agents/skills/shadcn-svelte/SKILL.md`. `web/.claude` is a symlink to
  `web/.agents`.
- `backend/`: `backend/README.md` documents the catalog layout, courses, and the
  sync protocol.

## Repository conventions

- Workflows live only in the root `.github/workflows/` and are path-filtered per
  project.
- `v*` tags are app releases and trigger the APK workflow. Tag other parts with a
  prefix such as `backend-v0.1.0`. The website's download button points at
  `releases/latest`, so non-app releases must not be marked latest.
- One `LICENSE` at the root covers every project.
