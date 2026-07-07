# 終焉 · Shuuen — Web Frontend

Web frontend for **Shuuen**, a next-generation ear-training app for musicians
(Windows · Linux · Android). It pairs with the native app and (in time) a
separate Go backend API. The current codebase is the **marketing landing
page** — a single, prerendered page with a monochrome, pure-black aesthetic —
built to grow into the interactive site (personal pages, news blog, a global
repository of melodies/levels/contexts, and music-API integrations).

> **Current scope:** UI only. The download / login / repository actions are
> presentational for now. The rendering setup and URL scheme are already
> arranged for the dynamic features to drop in later — see
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
| Rendering      | Hybrid — marketing prerendered, app routes SSR — via `@sveltejs/adapter-node` |
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
cp .env.example .env   # local config (PUBLIC_SITE_URL); adjust if needed
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
| `bun run build`    | Build the Node server to `build/` (marketing routes prerendered). |
| `bun run preview`  | Serve the production build locally to sanity-check it.          |
| `bun run check`    | Type-check with `svelte-check` (0 errors expected).             |
| `bun run check:watch` | Same, in watch mode.                                         |

---

## Project structure

```
.env(.example)              # PUBLIC_SITE_URL (used to build the app-pairing link)
src/
├─ app.html                 # Document shell — `<html class="dark">` (dark-only brand)
├─ routes/
│  ├─ +layout.svelte        # Root layout (all pages): favicon + global CSS
│  ├─ layout.css            # Tailwind + shadcn theme tokens (pure-black override)
│  └─ (marketing)/          # Prerendered marketing route group (URL-transparent)
│     ├─ +layout.ts         # `export const prerender = true` for this group
│     └─ +page.svelte       # The landing page (served at `/`)
│                           # → future: sibling (app)/ group for dynamic, SSR routes
└─ lib/
   ├─ components/ui/        # shadcn-svelte components (button, card, separator)
   └─ utils.ts              # `cn()` class-merge helper

static/images/             # Brand assets (logo wordmark, app icon / favicon)
docs/                      # Architecture, design language, contributing guide
```

---

## Rendering & deployment

Rendering is **hybrid** (`@sveltejs/adapter-node`): marketing routes are
prerendered to HTML at build time and served as static files, while future app
routes render on the server at request time. `bun run build` produces a
self-contained Node app in `build/`:

```
build/
├─ index.js        # server entry — start with `node build`
├─ handler.js      # Express/Connect-style middleware (for embedding)
├─ prerendered/    # prebuilt marketing HTML (e.g. index.html)
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
