# 終焉 · Shuuen — Web Frontend

Web frontend for **Shuuen**, a next-generation ear-training app for musicians
(Windows · Linux · Android). It pairs with the native app and (in time) a
separate Go backend API. The current codebase includes the **marketing landing
page** plus a small optional-auth surface (login, sign-up, and profile) built
to grow into the interactive site (personal pages, news blog, a global
repository of melodies/levels/contexts, and music-API integrations).

> **Current scope:** marketing UI plus optional account flow. Download and
> repository actions are still presentational; auth routes call the Go backend
> and store its JWT in an HTTP-only frontend session cookie. The rendering setup
> and URL scheme are arranged for more dynamic features — see
> [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Stack

| Concern        | Choice                                                                 |
| -------------- | ---------------------------------------------------------------------- |
| Framework      | [SvelteKit](https://svelte.dev/docs/kit) (Svelte 5, runes mode)        |
| UI components  | [shadcn-svelte](https://shadcn-svelte.com) — `nova` style, `neutral` base |
| Styling        | [Tailwind CSS v4](https://tailwindcss.com)                             |
| Icons          | [Lucide](https://lucide.dev) (`@lucide/svelte`)                        |
| Font           | Inter (variable, self-hosted via `@fontsource-variable/inter`)         |
| Runtime / PM   | [Bun](https://bun.sh)                                                   |
| Rendering      | SSR via `@sveltejs/adapter-node` with same-origin backend proxying |
| Backend        | Separate **Go** API (not in this repo), reached at `/api` (see [ARCHITECTURE](docs/ARCHITECTURE.md)) |

The visual design was imported from a
[Claude Design](https://claude.ai/design) project and adapted to the default
shadcn-svelte look. See [docs/DESIGN.md](docs/DESIGN.md).

---

## Getting started

**Prerequisites:** [Bun](https://bun.sh) `≥ 1.3`. (Node `≥ 22` also works if you
prefer `npm`/`pnpm`; `engine-strict` is enabled.)

```sh
bun install            # install dependencies
cp .env.example .env   # local config (PUBLIC_SITE_URL, SHUUEN_BACKEND_URL)
bun run dev            # start the dev server at http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173). Hot-module reload is on.

> In dev, requests to `/api` are proxied to a local Go backend at
> `http://localhost:9999` (configured in [`vite.config.ts`](vite.config.ts)).
> The backend isn't required to view the marketing page.

---

## Scripts

| Command            | What it does                                                     |
| ------------------ | --------------------------------------------------------------- |
| `bun run dev`      | Start the Vite dev server (HMR) on port `5173`.                 |
| `bun run build`    | Build the Node server to `build/`.                              |
| `bun run preview`  | Serve the production build locally to sanity-check it.          |
| `bun run check`    | Type-check with `svelte-check` (0 errors expected).             |
| `bun run check:watch` | Same, in watch mode.                                         |

---

## Project structure

```
.env(.example)              # PUBLIC_SITE_URL + server-only backend URL
src/
├─ app.html                 # Document shell — `<html class="dark">` (dark-only brand)
├─ routes/
│  ├─ +layout.svelte        # Root layout (all pages): favicon + global CSS
│  ├─ layout.css            # Tailwind + shadcn theme tokens (pure-black override)
│  ├─ (marketing)/          # Session-aware marketing route group (URL-transparent)
│  │  ├─ +layout.ts         # `export const prerender = false` for account state
│  │  └─ +page.svelte       # The landing page (served at `/`)
│  └─ (app)/                # Dynamic SSR account routes (`/login`, `/sign-up`, `/me`)
└─ lib/
   ├─ auth/                 # Shared auth response types
   ├─ components/ui/        # shadcn-svelte components
   ├─ server/               # Server-only auth/backend helpers
   └─ utils.ts              # `cn()` class-merge helper

static/images/             # Brand assets (logo wordmark, app icon / favicon)
docs/                      # Architecture, design language, contributing guide
```

---

## Rendering & deployment

Rendering is SSR through `@sveltejs/adapter-node`: the landing page renders on
request so the header can reflect the current login state, and app routes render
on the server too. `bun run build` produces a self-contained Node app in
`build/`:

```
build/
├─ index.js        # server entry — start with `node build`
├─ handler.js      # Express/Connect-style middleware (for embedding)
├─ client/         # hashed JS, CSS, fonts, images
└─ server/         # SSR server bundle
```

Run it with a `PORT` (default `3000`):

```sh
PORT=3000 node build
```

**Production topology (self-hosted):** a reverse proxy (Caddy/Nginx) terminates
TLS on `:443` and routes by path — `/` and app routes to this Node server,
`/api` (and the `/link` alias) to the Go backend on an internal port. The proxy
is a **production-only** concern; locally, Vite's dev proxy fills that role.
Full details, including the URL scheme, in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how the frontend, Go backend,
  and reverse proxy fit together: URL scheme (`/`, `/api`, `/link`), rendering
  model, and dev-vs-production wiring.
- [docs/DESIGN.md](docs/DESIGN.md) — the Shuuen design language: color, type,
  spacing, and the decisions made adapting the source design to shadcn-svelte.
- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) — dev conventions, how to add
  shadcn-svelte components, and code style.
