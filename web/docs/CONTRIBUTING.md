# Contributing

A short guide to working in this repo. It's a small, UI-only SvelteKit project;
the goal is to keep it consistent with the shadcn-svelte preset and the
[Shuuen design language](DESIGN.md).

## Setup

```sh
bun install
bun run dev      # http://localhost:5173
```

Before pushing, make sure the project type-checks and builds:

```sh
bun run check    # svelte-check — expect 0 errors / 0 warnings
bun run build    # must prerender cleanly to build/
```

## Adding shadcn-svelte components

Use the CLI — don't hand-write component files. It reads `components.json` and
installs into `src/lib/components/ui/`:

```sh
bunx --bun shadcn-svelte@latest add <component>   # e.g. dialog, badge, tooltip
```

Then import per the barrel style:

```ts
// Single-component barrels → named import
import { Button } from '$lib/components/ui/button';
import { Separator } from '$lib/components/ui/separator';

// Multi-part components → namespace import
import * as Card from '$lib/components/ui/card';
// <Card.Root> <Card.Header> <Card.Footer> …
```

## Styling conventions

Follow the shadcn-svelte rules — they keep the UI coherent:

- **Semantic tokens, not raw colors.** Use `bg-card`, `text-muted-foreground`,
  `border-border` — never `bg-neutral-900` or hex values.
- **`class` is for layout, not for restyling components.** Don't override a
  component's colors or typography through `class`.
- **Spacing with `gap-*`**, not `space-x/y-*`. Vertical stacks:
  `flex flex-col gap-*`.
- **`size-*`** when width == height (`size-6`, not `w-6 h-6`).
- **`cn()`** (from `$lib/utils`) for conditional classes.
- **Icons in a `Button`** get `data-icon="inline-start"` / `"inline-end"` and
  **no size classes** — the component sizes them. Standalone icons may take a
  `size-*` class.

## Theme changes

The theme lives in [`src/routes/layout.css`](../src/routes/layout.css)
(shadcn CSS variables). The brand is **dark-only** and pinned to a pure-black
`--background`; keep it that way unless the brand changes. Don't add a second
global stylesheet.

## Rendering

The site is fully prerendered (`export const prerender = true` in
[`+layout.ts`](../src/routes/+layout.ts), `adapter-static`). Keep new routes
prerenderable. If you add internal anchor links (`#section`), make sure the
target `id` exists, or the prerender crawler will flag it.

## Commit style

Short, imperative subject lines (e.g. `add download section`, `fix hero button
padding`). Group related changes.
