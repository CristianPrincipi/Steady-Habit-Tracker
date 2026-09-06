# docs/tokens.md — Design tokens extracted from the Figma Make prototype

Source of truth: `docs/figma-source/` (verbatim cache of the Make file).
Extracted in M0.6 / M0.7. **Do not re-fetch from Figma to answer a question this file answers** — see the MCP budget note at the bottom.

---

## 0. Where the tokens actually live

The prototype defines tokens in **two** places, and they disagree:

| Source | What it holds | Does the UI use it? |
| --- | --- | --- |
| `src/styles/theme.css` | a full shadcn token set (`--background`, `--card`, `--primary`, …) | **Barely.** Only via `@layer base` on bare HTML elements. |
| `src/app/App.tsx` | hardcoded hex + Tailwind utility classes | **Yes — this is what renders.** |

Every screen in `App.tsx` writes colours literally (`bg-[#1c1c1f]`, `text-white/25`, `style={{ backgroundColor: habit.color }}`). The theme variables are almost decorative.

**Consequence for the Compose port:** `theme.css` and `App.tsx` agree on the important values (`--background: #111113`, `--card: #1c1c1f`, `--primary: #4A9EFF`), so we port that palette as the canonical one. Where they diverge, **`App.tsx` wins** — it is what the designer actually looked at.

### What the designer actually customised

Reliable heuristic, no diff against `default_shadcn_theme.css` required:

- **Hex value (`#111113`) → hand-authored by the designer.** Port it.
- **`oklch(...)` still present → untouched shadcn default.** Ignore it.

In `:root` the entire `--sidebar-*` group and `--chart-1..5` are still `oklch` — i.e. leftovers the designer never touched. In `.dark`, `--chart-1..5` *were* rewritten to the habit palette. There is no sidebar in this app, so the whole `--sidebar-*` group is dead.

---

## 1. Colour

### 1.1 Surfaces (dark — the only theme that exists)

| Token | Value | Used for |
| --- | --- | --- |
| `page` | `#0a0a0c` | Page backdrop *outside* the simulated phone frame. **Web-only — drop it on Android.** |
| `background` | `#111113` | The app background. This is the real one. |
| `card` / `surface` | `#1c1c1f` | Every card, tile, input, popover |
| `secondary` | `#2a2a2e` | Secondary fills |
| `switchBackground` | `#3a3a3e` | Switch track (unused in the built screens) |
| `border` | `rgba(255,255,255,0.06)` | Every card border, nav top divider |
| `borderInput` | `rgba(255,255,255,0.08)` | Text-field borders |
| `borderInputFocus` | `rgba(255,255,255,0.20)` | Text-field focus border |

### 1.2 Brand & semantic

| Token | Value | Used for |
| --- | --- | --- |
| `primary` | `#4A9EFF` | Connect button, Month nav accent, default habit colour |
| `ring` | `rgba(74,158,255,0.4)` | Focus ring |
| `destructive` | `#ff4d4d` | Declared in `theme.css`; **never used in any screen** |
| `success` | Tailwind `green-400` ≈ `#4ADE80` | Connected status dot |
| `warning` | Tailwind `amber-500` ≈ `#F59E0B` | CORS warning banner text/border |

### 1.3 The habit palette

Each habit carries its own colour; it drives its icon tint, dots, pills, chips, stat icons and chart gradient. Also mirrored into `--chart-1..5` in `.dark`.

| Name | Hex | Used by |
| --- | --- | --- |
| Blue | `#4A9EFF` | Learn Guitar · Month tab |
| Green | `#76C442` | Drink water · Week tab |
| Orange | `#FF7A35` | Morning Yoga · Year tab |
| Purple | `#8B5CF6` | Read before sleep · Stats tab |
| Amber | `#F59E0B` | `--chart-5` only — no habit uses it |
| Grey | `#888888` | Settings tab icon (not a habit colour) |

### 1.4 Habit-colour alpha ramp

The prototype tints by appending a hex alpha byte to the habit colour. Port these as explicit alpha values:

| Suffix | Alpha | Where |
| --- | --- | --- |
| `28` | 0.157 | `HabitIcon` circle background |
| `26` | 0.149 | Month dot, incomplete |
| `22` | 0.133 | Year dot incomplete · stat-tile icon chip |
| `20` | 0.125 | Week pill incomplete · unselected habit chip |

> ⚠ **M13.4 added an outline the prototype does not draw**: an unticked week pill at `20` is
> **1.21:1** against its card, and SC 1.4.11 wants 3:1 for a control's state. The fill could not
> carry it — 3:1 needs alpha `0.79` for purple, which draws unticked at nearly the ticked colour —
> so unticked pills gain a white hairline at `0.35` (3.20:1). The fill above is unchanged.


### 1.5 Foreground / text opacity ramp

All text is white at an opacity. This is a real ramp and should become named tokens:

| Opacity | Used for |
| --- | --- |
| `1.00` | Headings, habit names, stat values, completion % |
| `0.55` | Schema property `<code>` chips |
| `0.50` | "Not connected" label |
| `0.40` | Nav-arrow date label |
| `0.35` | Habit description (Week/Year) · field labels · nav arrow icon |
| `0.30` | Habit description (Month) · stat-tile label |
| `0.25` | Section eyebrows · weekday initials · sub-captions |
| `0.22` | Field helper text · chart axis ticks · inactive bottom-nav item |
| `0.15` | Input placeholder |

> ### ⚠⚠ The app no longer ships these values — M13.4 re-spaced the ramp for WCAG AA (ADR-111)
>
> **This table stays as the record of what the prototype draws.** It is the extraction, and the
> extraction should not be edited to match a later decision — but reading it and typing these numbers
> into `Color.kt` would now undo that decision, so the departure is recorded here rather than only in
> the ADR.
>
> **Six of these nine are below WCAG AA on the design's own background**: `0.40` is 3.81:1, `0.35` is
> 3.19, `0.30` is 2.66, `0.25` is 2.20, `0.22` is 1.97 and `0.15` is 1.54, against the 4.5 that
> normal text requires. Every one is drawn at 9–14sp, so none reaches the 3:1 large-text allowance.
>
> ⚠ **And they cannot be nudged.** The minimum alpha for 4.5:1 over both `#111113` and `#1c1c1f` is
> `0.46`, so all six collapse onto one value — nine levels of hierarchy become four. The ramp is
> therefore re-spaced by contrast *ratio*, 17:1 down to 4.6:1, which keeps nine distinct levels.
>
> The shipped values live in `TextEmphasis` and are pinned by `ContrastTest`. One level, `disabled`,
> is deliberately left at the old `0.30` under WCAG 1.4.3's exemption for inactive controls.

---

## 2. Typography

`src/styles/fonts.css` pulls both families from Google Fonts:

- **DM Sans** — weights 300, 400, 500, 600, 700 + italic 400. The UI face.
  `fontFamily: "'DM Sans', -apple-system, sans-serif"` on the app root.
- **DM Mono** — weights 400, 500. Used **only** for the token field, the database-ID field, and the schema property chips.

> **Android:** bundle both as local `res/font` resources. `CLAUDE.md §8` forbids contacting any host but `api.notion.com`, so a runtime Google Fonts fetch is out. Both families are SIL Open Font License.

### 2.1 Size scale

`theme.css` sets `--font-size: 16px` as the html base and references `--text-2xl` / `--text-xl` / `--text-lg` / `--text-base`, **but never defines them** — so those resolve to Tailwind's defaults. And because `App.tsx` styles everything with utility classes, only the utility scale matters:

| Class | px | Line height | Used for |
| --- | --- | --- | --- |
| `text-[9px]` | 9 | — | "COMPLETION" eyebrow under the % |
| `text-[10px]` | 10 | — | Eyebrows, weekday initials, helper text, bottom-nav labels, schema rows |
| `text-xs` | 12 | 16 | Habit name (Month card), descriptions, chips, banner text |
| `text-sm` | 14 | 20 | Habit name (Week/Year), nav-arrow label, inputs, buttons |
| `text-base` | 16 | 24 | `HabitIcon` emoji, small variant |
| `text-xl` | 20 | 28 | `HabitIcon` emoji, large variant |
| `text-2xl` | 24 | 32 | Screen title · completion % |
| `text-3xl` | 30 | 36 | Stat-tile value |

### 2.2 Weights

| Class | Weight | Used for |
| --- | --- | --- |
| `font-medium` | 500 | Nav-arrow label, weekday initials, connection status |
| `font-semibold` | 600 | Habit names, eyebrows, chips, buttons, bottom-nav labels |
| `font-bold` | 700 | Screen title, stat values, completion % |

`theme.css` redefines `--font-weight-medium: 600`, but that only affects bare `h1`–`h4`/`label`/`button`/`input`, which utilities override everywhere. **Use the utility weights above.**

### 2.3 Other type treatments

- `tracking-tight` on the screen title (Tailwind `-0.025em`).
- `tracking-widest` (`0.1em`) + `uppercase` on every eyebrow label.
- `tracking-wide` (`0.025em`) on weekday initials.
- `font-variant-numeric: tabular-nums` on stat values and the completion % — **carry this over**, it stops digits jittering. In Compose: a `TextStyle` with `FontFeatureSetting("tnum")`.

---

## 3. Shape / corner radius

`theme.css` sets `--radius: 0.625rem` (10px) and remaps Tailwind's `sm`/`md`/`lg`/`xl` through `@theme inline`. **It does not remap `2xl`.** So:

| Class | Resolves to | Used for |
| --- | --- | --- |
| `rounded-sm` | `calc(10px - 4px)` = **6px** | (unused) |
| `rounded-md` | `calc(10px - 2px)` = **8px** | Schema property `<code>` chip |
| `rounded-lg` | `var(--radius)` = **10px** | Stat-tile icon chip |
| `rounded-xl` | `calc(10px + 4px)` = **14px** | Week pill, nav arrow button, inputs, Connect button, warning banner |
| `rounded-2xl` | **16px** (Tailwind default — *not* remapped) | Every card and tile |
| `rounded-[3px]` | 3px | Month dot |
| `rounded-full` | pill / circle | Habit icon, `+` button, habit chips, status dot, nav active dot |
| inline `borderRadius: 1` | 1px | Year dot |
| `sm:rounded-[40px]` | 40px | Simulated phone frame — **web-only, drop it** |

That 14px-not-12px result for `rounded-xl` is easy to get wrong; it is the single most load-bearing radius after the 16px card.

---

## 4. Spacing

Tailwind default scale (1 unit = 4px).

### 4.1 Layout

| Value | Where |
| --- | --- |
| 16px (`p-4`) | Standard content padding for every scrollable view |
| 20px (`px-5`) | Header and nav-arrow row horizontal padding |
| 40px (`pt-10`) | Header top padding — **replace with status-bar inset on Android** |
| 112px (`pb-28`) | Scroll-content bottom padding, clearing the floating bottom nav |

### 4.2 Cards

| Value | Where |
| --- | --- |
| 14px (`p-3.5`) | Month habit card |
| 16px (`p-4`) | Week / Year / stat / settings cards |
| 12px (`space-y-3`) | Gap between card sections; grid gap between cards |
| 16px (`space-y-4`) | Week card internal spacing; Stats view section spacing |
| 20px (`space-y-5`) | Settings view section spacing |

### 4.3 Grids

| Value | Where |
| --- | --- |
| 3px | Month dot grid gap |
| 2px | Year dot grid gap |
| 6px (`gap-1.5`) | Week pill grid gap |
| 8px (`gap-2`) | Habit-chip row gap |
| 12px (`gap-3`) | Month card grid gap (2 columns), stat-tile grid gap (2 columns) |

### 4.4 Component sizes

| Size | Component |
| --- | --- |
| 8px | Connection status dot (`w-2 h-2`) |
| 4px | Bottom-nav active indicator dot (`w-1 h-1`) |
| 28px | Stat-tile icon chip (`w-7 h-7`) |
| 32px | Small habit icon; nav arrow button (`w-8 h-8`) |
| 36px | `+` button (`w-9 h-9`); week pill height (`h-9`) |
| 40px | Large habit icon (`w-10 h-10`) |
| 4px / 5px / 6px | Year dot size — Year card uses 4, Stats "Year overview" uses 6, default 5 |

> **Touch-target warning:** the 32px nav-arrow buttons and 36px `+` button are **below Android's 48dp minimum** (`CLAUDE.md §2`). Keep the *visual* size and expand the touch target — in Compose, `Modifier.size(48.dp)` on the clickable with the drawn shape sized inside, or `minimumInteractiveComponentSize()`.

---

## 5. Iconography

`lucide-react`, 13 icons total:

| Icon | Where | Size / stroke |
| --- | --- | --- |
| `LayoutGrid` `AlignJustify` `Calendar` `BarChart2` `Settings` | Bottom nav | 22px, stroke 2.2 active / 1.5 inactive |
| `Plus` | Header add button | 18px |
| `ChevronLeft` `ChevronRight` | Month/Week nav arrows | 18px |
| `Hash` `Percent` `Flame` `Trophy` | Stat tiles | 13px |
| `Check` | Completed week pill | 13px, stroke 3, white |

Lucide is ISC-licensed. Closest Material Symbols equivalents exist for all of them, but Lucide's geometry is distinctive (notably `Flame` and `Trophy`) — **ask the user** whether to ship Lucide SVGs as vector drawables or substitute Material Symbols.

The variable stroke width across nav states (2.2 vs 1.5) has no direct Compose `ImageVector` equivalent for a static asset — it needs either two drawables per icon or a `Canvas`-drawn path with parameterised `strokeWidth`.

---

## 6. Motion

Everything is a CSS transition; there is no keyframe animation, no spring, no shared-element transition.

| Effect | Value | Android translation |
| --- | --- | --- |
| `active:scale-90` | scale to 0.90 while pressed | Press-scale via `animateFloatAsState` on `interactionSource.collectIsPressedAsState()` |
| `active:scale-95` | scale to 0.95 (habit chips) | as above |
| `active:scale-[0.98]` | scale to 0.98 (Connect button) | as above |
| `transition-all` / `transition-opacity` / `transition-colors` | default 150ms ease | `MaterialTheme` default easing; keep it short |
| `hover:*` | pointer hover states | **No Android equivalent — drop.** Replaced by ripple/press-scale. |
| `backdrop-blur-xl` on bottom nav | 24px backdrop blur over `#111113`/95 | ⭐ **NOT PORTED — the user's call, 2026-08-27 (ADR-066).** The opaque `#111113` bar is not a fallback any more, it is the v1 design, at every API level. A real backdrop blur needs `RenderEffect` (API 31+, `minSdk` is 26) or a dependency; the user looked at the flat bar in M5.6 and accepted it. |

---

## 7. Things that do not survive the port

| Prototype detail | Why it dies |
| --- | --- |
| `#0a0a0c` page backdrop + `max-w-sm` + `sm:rounded-[40px]` frame | A simulated phone bezel inside a browser. Android *is* the phone. |
| `max-h-[900px]` | Fixed viewport height; Android is edge-to-edge and variable. |
| `scrollbarWidth: "none"` | No web scrollbars to hide. |
| `hover:` states | No pointer. |
| Absolutely-positioned bottom nav | Becomes a real `NavigationBar` with navigation-bar insets; `pb-6` (24px) is standing in for the iOS home indicator. |
| `pt-10` header padding | Becomes the status-bar inset. |
| Google Fonts `@import` | Bundle the fonts; `CLAUDE.md §8` allows no host but `api.notion.com`. |
| `recharts` `AreaChart` | Hand-rolled Compose `Canvas` per `CLAUDE.md §4`. |

---

## 8. Figma MCP budget note

This account is **Figma Starter + View seat = 20 read calls per MONTH** (Dev/Full seats get 200/day). Confirmed via `whoami` + `file://figma/docs/rate-limits-access.md`.

Everything needed to build the UI is now cached in `docs/figma-source/`. **Read that, not Figma.**

Still un-fetched, deliberately deferred until the assets are actually needed (M2):

- the 4 PNGs (`8deb99bb…`, `8fc7b389…`, `ef9b4d61…`, `f713d396…`)
- `ATTRIBUTIONS.md` (asset licences — required before shipping any of those images)
- `default_shadcn_theme.css` (skipped for good: see §0, the hex-vs-oklch heuristic makes the diff redundant)
- the ~50 `components/ui/*.tsx` shadcn primitives (skipped for good: `CLAUDE.md §2` — port tokens, not components)

Note: `src/styles/globals.css` is listed in the MCP manifest but **404s** when read. It does not exist. Do not spend a call retrying it.
