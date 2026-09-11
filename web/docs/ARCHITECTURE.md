# Architecture

How the Shuuen web frontend fits together with the Go backend
([`backend/`](../../backend) in this monorepo), and how
requests are routed. This is the target design; today only the marketing page
exists, but the wiring below is already in place so the rest drops in cleanly.

## Pieces

| Piece | What it is | Where it listens |
| --- | --- | --- |
| **Web frontend** | [`web/`](..) — SvelteKit, `adapter-node` | Node server, internal `:3000` |
| **Backend API** | **Go** service in [`backend/`](../../backend) | internal `:9999` |
| **Reverse proxy** | Caddy ([`Caddyfile`](../../Caddyfile)) — TLS + routing | public `:443` (prod only) |

Both apps are self-hosted on one machine with Docker Compose
([`compose.prod.yaml`](../../compose.prod.yaml)). Only the reverse proxy is
exposed to the internet; the Node server and Go API are reachable only on the
Compose network, as `web:3000` and `api:9999`.

```
                       ┌──────────────────────────────────────┐
  https://shuuen.xyz   │  Reverse proxy (Caddy)        :443    │
  ─────────────────────▶  terminates TLS, owns the domain      │
                       │                                        │
                       │   /            → web frontend  ──────────▶  web:3000  (node build)
                       │   /app/*, …    → web frontend  ──────────▶  web:3000
                       │   /api/*       → Go backend    ──────────▶  api:9999
                       │   /link        → Go /api/link  ──────────▶  api:9999  (alias)
                       └──────────────────────────────────────┘
```

## URL scheme

Everything lives under one origin (`https://shuuen.xyz`) — same-origin means the
browser client never hits CORS.

| Path | Served by | Purpose |
| --- | --- | --- |
| `/` | frontend (SSR) | Marketing landing page with account-aware header |
| `/app/*`, `/me`, `/news`, … | frontend (SSR, later) | Personal pages, blog, melody/level repository |
| `/api/*` | Go backend | All API endpoints — repositories, melodies, contexts, music-API integrations, auth |
| `/link` | Go backend (`/api/link`) | **Cosmetic alias.** The URL the native app pastes to pair with the site. The proxy rewrites `/link` → `/api/link`; the short form just reads nicely for users. |

The copyable link on the landing page is `${PUBLIC_SITE_URL}/link` (see
[Configuration](#configuration)). It's consumed by the **native app**, not a
browser — so it only needs to be a clean, stable HTTPS URL.

## Rendering model

Rendering is decided **per route** (SvelteKit). The landing page now renders on
request so it can show the current account state in the header.

- **Marketing** — the [`(marketing)`](../src/routes) route group carries
  `export const prerender = false` in its `+layout.ts`, and `/` loads
  `locals.user` from the server session.
- **App** — dynamic routes live in the sibling `(app)/` group without
  `prerender` (SSR is the default). Route groups don't affect URLs, so `/`
  stays `/`.
  - Public, SEO-relevant pages (news blog, melody repository) → `+page.ts`
    universal `load` that fetches from the API.
  - Authenticated pages (personal pages) → `+page.server.ts` `load` +
    `hooks.server.ts` for session handling; server-only so secrets stay server-side.
  - Current account routes: `/login`, `/sign-up`, and `/me`. The frontend keeps
    the backend JWT in an HTTP-only cookie and sends it to Go as a bearer token
    from server code only.

`@sveltejs/adapter-node` produces one Node server that serves both the marketing
page and app routes through SSR.

### Where the frontend calls the API

- **Browser (client) code** calls the **same-origin** `/api` path. The proxy
  (prod) or Vite dev proxy (local) forwards it to Go. No CORS, no API base URL
  to configure.
- **Server-side `load`** can call Go directly on the internal address
  (`http://127.0.0.1:9999`) for lower latency, using SvelteKit's `event.fetch`.

## Configuration

Client-visible config comes from `.env` (`PUBLIC_` prefix = exposed to the
browser):

| Variable | Example | Used for |
| --- | --- | --- |
| `PUBLIC_SITE_URL` | `https://shuuen.xyz` | Building the `/link` pairing URL shown on the landing page |
| `SHUUEN_BACKEND_URL` | `http://127.0.0.1:9999` | Server-side auth calls from SvelteKit to the Go backend, and the target of Vite's dev `/api` proxy |
| `ORIGIN` | `https://shuuen.xyz` | Production only: the public origin. Behind the proxy, adapter-node needs it, or SvelteKit rejects form posts as cross-site |

Server-only secrets (DB URL, session keys, upstream music-API keys) are added
**without** the `PUBLIC_` prefix and read via `$env/dynamic/private` in server
code. Copy `.env.example` → `.env` to start.

## Dev vs. production

|  | Development | Production |
| --- | --- | --- |
| Frontend | `bun run dev` (Vite, `:5173`) | `node build` (`:3000`) behind the proxy |
| Backend | `go run ./cmd/api` in `backend/` (`:9999`) | Go binary (`:9999`) behind the proxy |
| `/api` routing | **Vite dev proxy** (`vite.config.ts`) → `:9999` | **Reverse proxy** → `:9999` |
| TLS | none (plain http) | terminated at the proxy |

So the **reverse proxy only runs in production** — its job (TLS + same-origin
routing between the two apps) is handled locally by Vite's dev proxy. You do not
need Caddy to develop.

`docker compose up --watch` at the repository root runs the development column
in containers, with Postgres. `compose.prod.yaml` runs the production column
behind Caddy.

### Production proxy (Caddy)

The root [`Caddyfile`](../../Caddyfile) sends `/api/*` to `api:9999`, rewrites
`/link` to `/api/link` on the same backend, and sends everything else to
`web:3000`. Caddy obtains the TLS certificate for `SITE_DOMAIN` automatically.

## Backend follow-up checklist

1. Add the `/api/link` endpoint for the native app pairing alias.
2. Expand the `(app)/` route group with public repository/news routes and their `load`s.
3. Add stronger production session management if the frontend needs refresh tokens or
   long-lived sessions.
4. Make the API trust `X-Forwarded-For` from Caddy (and from the web server's
   auth calls), so per-IP rate limits see client addresses instead of the
   proxy's.
