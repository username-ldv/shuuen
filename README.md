<img width="481" height="316" alt="Shuuen logo" src="app/source-images/Shuuen%20Main%20Logo.png" />

# 終焉 · Shuuen

Shuuen is an ear-training app for musicians. This monorepo holds every part of it.

[Discord](https://discord.gg/Qybq9qjcSJ) · [Latest release](https://github.com/username-ldv/shuuen/releases/latest)

| Folder | What it is | Stack |
| --- | --- | --- |
| [`app/`](app) | The ear-training app for Android and desktop (Windows · Linux) | Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor |
| [`backend/`](backend) | API for accounts, the melody catalog, courses, and sync | Go, Fiber v3, GORM, JWT, Postgres |
| [`web/`](web) | Website: landing page and account pages | SvelteKit (Svelte 5), shadcn-svelte, Tailwind CSS v4, Bun |

## How the parts connect

- The backend listens on `:9999`. [`backend/openapi.yaml`](backend/openapi.yaml)
  is the API contract that both clients follow.
- The app calls the backend directly. The default URL is `http://10.0.2.2:9999`
  on the Android emulator and `http://127.0.0.1:9999` on desktop, and users can
  change it in the app.
- The website calls same-origin `/api`. Locally, Vite's dev proxy forwards it to
  `:9999`; in production a reverse proxy does. See
  [`web/docs/ARCHITECTURE.md`](web/docs/ARCHITECTURE.md).

## Running locally

To run the backend and website together, start Docker Desktop (or another Docker
engine) and run this from the repository root:

```sh
docker compose up --watch
```

It starts Postgres, the API at http://localhost:9999, and the website at
http://localhost:5173. Saving a Go file rebuilds the API container, and changes
under `web/src` hot-reload in the browser. The development settings are in
[`compose.override.yaml`](compose.override.yaml), including the bootstrap
administrator (`admin` / `change-this-development-password`). Load the test
courses with `docker compose exec api ./seed-c-tonic`. The API reads the melody
catalog from `backend/data/`, the same folder `go run` uses. Stop with Ctrl+C or
`docker compose down`; add `-v` to also delete the database.

Each project also runs with its own toolchain. Run commands from its folder:

```sh
# Backend (http://localhost:9999); needs the database from `docker compose up -d postgres`
cd backend && cp .env.example .env && go run ./cmd/api

# Website (http://localhost:5173)
cd web && bun install && cp .env.example .env && bun run dev

# Desktop app (or open app/ in Android Studio)
cd app && ./gradlew :desktopApp:run
```

Each folder's README has the details.

## Production

[`compose.prod.yaml`](compose.prod.yaml) runs the same services behind
[Caddy](https://caddyserver.com), which obtains the TLS certificate and routes
`/api` and `/link` to the API and everything else to the website (see the
[`Caddyfile`](Caddyfile)). Only ports 80 and 443 are published. On the server:

```sh
cp .env.example .env    # set the domain and secrets
docker compose up -d --build
```

`.env` sets `COMPOSE_FILE`, so every `docker compose` command on the server uses
the production files. Back up the `postgres-data` and `api-data` volumes
together.

## CI and releases

| Workflow | Runs on |
| --- | --- |
| [`backend-ci.yml`](.github/workflows/backend-ci.yml) | Pushes and PRs that touch `backend/` |
| [`web-ci.yml`](.github/workflows/web-ci.yml) | Pushes and PRs that touch `web/` |
| [`android-release-apk.yml`](.github/workflows/android-release-apk.yml) | `v*` tags or a manual run; builds the signed APK and publishes a GitHub Release |

`v*` tags are app releases. Other parts use prefixed tags such as
`backend-v0.0.1`. The website's download button links to
`releases/latest`, so publish any non-app release with `--latest=false`.

## History

`backend/` and `web/` were separate repositories
([shuuen-backend](https://github.com/username-ldv/shuuen-backend),
[shuuen-frontend](https://github.com/username-ldv/shuuen-frontend)) until
September 2026. Their full commit history was imported here.

## License

[The Supreme Mandate of the Void](LICENSE), covering the whole repository.
