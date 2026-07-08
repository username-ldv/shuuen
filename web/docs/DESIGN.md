# Shuuen — Design Language

Shuuen (**終焉**, Japanese for *"the end / final moment"*) is a quiet, precise,
**monochrome** brand. The landing page leans on that identity: pure black,
generous letter-spacing, uppercase labels, and no decorative color.

---

## Color

The brand is **monotone** — neutrals carry the entire UI. There is no accent
color on this page.

- **Canvas is true black.** The shadcn dark theme's `--background` is overridden
  to `oklch(0 0 0)` in `layout.css`. This matters for the brand *and* for the
  assets: the logo/app-icon PNGs carry a black background with a soft glow, so a
  non-black canvas would reveal a visible square around them.
- **Dark-only.** `<html>` is hard-set to `class="dark"` in `app.html`; there is
  no light theme and no theme toggle.
- **Surfaces** (cards, the repo URL field) use the shadcn `--card` token — a
  slightly lifted near-black. Depth is communicated by *brighter fill*, never by
  shadows.
- **Hairlines, not borders.** Separators and edges use `border`/`ring` tokens at
  low opacity (white-on-black hairlines).

Semantic tokens are used throughout (`bg-background`, `text-muted-foreground`,
`bg-card`, `border-border`) instead of raw hex, so the page stays consistent
with the preset.

> In the full app, the *only* sanctioned color is answer feedback
> (correct / incorrect) and a 12-hue palette used exclusively to color musical
> notes. None of that appears on the marketing page.

---

## Typography

- **Font:** Inter (variable), self-hosted. The repo-URL value uses the default monospace stack.
- **Voice:** quiet and precise. Chrome labels are **UPPERCASE with wide
  tracking** (`tracking-[0.2em]`–`tracking-[0.28em]`); body copy is sentence
  case with relaxed line-height.
- **Weights:** limited to Regular / Medium / Semibold.
- **Balance:** the hero headline uses `text-balance`; body copy uses
  `text-pretty`.

---

## Spacing, radius, elevation

- **Rhythm:** a calm 4px-based scale (Tailwind spacing).
- **Content width:** a single centered column, `max-w-[720px]`.
- **Corners:** soft, not pill-for-show. Buttons keep the preset's `rounded-lg`
  rather than the fully-rounded pills of the reference — this was a deliberate
  call to favor the shadcn default.
- **Elevation:** none. No shadows anywhere; raised = brighter fill.

---

## Components & icons

| Reference element        | Implementation                                             |
| ------------------------ | ---------------------------------------------------------- |
| Primary button (white)   | shadcn `Button` `variant="default"` (inverts in dark)      |
| Secondary button         | shadcn `Button` `variant="secondary"`                      |
| Download platform cards  | shadcn `Card` (`Header` / `Footer` composition)            |
| Section divider          | shadcn `Separator`                                         |
| Icons                    | Lucide equivalents of the design's Material Symbols        |

**Icon mapping** (Material Symbols → Lucide): `login → LogIn`,
`account_circle → CircleUserRound`, `download → Download`, `link → Link2`,
`content_copy → Copy`, `check → Check`, `desktop_windows → Monitor`,
`smartphone → Smartphone`.

Icons inside a `Button` use `data-icon="inline-start"` (no size classes — the
component sizes them). See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Notable adaptations from the reference

1. **Pure-black canvas** kept (brand + asset requirement), but everything else
   rides the shadcn dark theme.
2. **Inter instead of Geist**, and default rounding instead of full pills —
   preferring the shadcn preset per the brief.
3. **Hero CTA sizing:** the hero buttons are enlarged (`h-11 px-6`). Because the
   preset trims a button's *leading* padding when it has a start icon
   (`has-data-[icon=inline-start]:pl-2`), the override also restores symmetric
   left padding (`has-data-[icon=inline-start]:pl-6`) so the label stays
   centered.
4. **"News" is a placeholder** nav item with no section yet.

---

## Assets

Brand assets live in [`static/images/`](../static/images):

- `shuuen-logo.png` — the 終焉 brush wordmark (white on transparent).
- `shuuen-icon.png` — the 終 app icon (also used as the favicon).

Originals are in `source-images/`.
