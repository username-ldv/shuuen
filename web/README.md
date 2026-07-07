# 終焉 · Shuuen — Landing

Marketing landing page for **Shuuen**, a next-generation ear-training app for
musicians (Windows · Linux · Android). The site is a single, statically
prerendered page with a monochrome, pure-black aesthetic.

> **Scope:** this repository is the **UI only** — the marketing page. It contains
> no application logic; the download / login / repository actions are
> presentational anchors.

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
| Rendering      | Fully prerendered (static) via `@sveltejs/adapter-static`              |

The visual design was imported from a
[Claude Design](https://claude.ai/design) project and adapted to the default
shadcn-svelte look. See [docs/DESIGN.md](docs/DESIGN.md).

---

## Getting started

**Prerequisites:** [Bun](https://bun.sh) `≥ 1.3`. (Node `≥ 22` also works if you
prefer `npm`/`pnpm`; `engine-strict` is enabled.)

```sh
bun install      # install dependencies
bun run dev      # start the dev server at http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173). Hot-module reload is on.

---

## Scripts

| Command            | What it does                                                     |
| ------------------ | --------------------------------------------------------------- |
| `bun run dev`      | Start the Vite dev server (HMR) on port `5173`.                 |
| `bun run build`    | Prerender the site to static files in `build/`.                 |
| `bun run preview`  | Serve the production `build/` locally to sanity-check it.       |
| `bun run check`    | Type-check with `svelte-check` (0 errors expected).             |
| `bun run check:watch` | Same, in watch mode.                                         |

---

## Project structure

```
src/
├─ app.html                 # Document shell — `<html class="dark">` (dark-only brand)
├─ routes/
│  ├─ +layout.svelte        # Root layout: favicon + global CSS
│  ├─ +layout.ts            # `export const prerender = true` (static site)
│  ├─ +page.svelte          # The entire landing page
│  └─ layout.css            # Tailwind + shadcn theme tokens (pure-black override)
└─ lib/
   ├─ components/ui/        # shadcn-svelte components (button, card, separator)
   └─ utils.ts              # `cn()` class-merge helper

static/images/             # Brand assets (logo wordmark, app icon / favicon)
docs/                      # Design language + contributing guide
```

---

## Rendering & deployment

The whole site is **prerendered to static HTML** at build time — there is no
server runtime. `bun run build` produces a self-contained `build/` directory:

```
build/
├─ index.html      # fully rendered page
├─ _app/           # hashed JS, CSS, fonts
├─ images/         # brand assets
└─ robots.txt
```

Deploy `build/` to any static host — GitHub Pages, Netlify, Cloudflare Pages,
Vercel (static), S3 + CloudFront, etc. No Node server required.

> If a future route needs runtime rendering (server data, form actions, API
> endpoints), set `export const prerender = false` on that route and swap
> `adapter-static` for a server-capable adapter in
> [`vite.config.ts`](vite.config.ts). See the
> [adapters docs](https://svelte.dev/docs/kit/adapters).

---

## Documentation

- [docs/DESIGN.md](docs/DESIGN.md) — the Shuuen design language: color, type,
  spacing, and the decisions made adapting the source design to shadcn-svelte.
- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) — dev conventions, how to add
  shadcn-svelte components, and code style.
