# docs/attributions.md — third-party assets shipped in the APK

Every asset bundled into the app, where it came from, and what its licence requires of us.
Add a row here **before** committing any new binary asset.

---

## Fonts

Both families are bundled as `res/font` resources rather than fetched at runtime. `CLAUDE.md §8`
permits the app to contact `api.notion.com` and no other host, so the prototype's Google Fonts
`@import` (`docs/figma-source/src/styles/fonts.css`) has no Android equivalent — see
`docs/tokens.md §2`.

| Asset | Family | Licence | Source |
| --- | --- | --- | --- |
| `res/font/dm_sans.ttf` | DM Sans 4.004, variable | SIL OFL 1.1 | [google/fonts `ofl/dmsans`](https://github.com/google/fonts/tree/main/ofl/dmsans) |
| `res/font/dm_mono_regular.ttf` | DM Mono 1.000, Regular | SIL OFL 1.1 | [google/fonts `ofl/dmmono`](https://github.com/google/fonts/tree/main/ofl/dmmono) |
| `res/font/dm_mono_medium.ttf` | DM Mono 1.000, Medium | SIL OFL 1.1 | as above |

Copyright holders, as recorded in each font's own `name` table:

- DM Sans — Copyright 2014 The DM Sans Project Authors (<https://github.com/googlefonts/dm-fonts>)
- DM Mono — Copyright 2020 The DM Mono Project Authors (<https://github.com/googlefonts/dm-mono>)

### What the OFL requires

The SIL Open Font License permits bundling and redistribution inside an application, including a
commercial one, on three conditions that matter to us:

1. **The licence and copyright notice travel with the font.** Both licence texts therefore ship
   inside the APK, at `assets/licences/DM-Sans-OFL.txt` and `assets/licences/DM-Mono-OFL.txt`, not
   merely in this repository. They are byte-for-byte the `OFL.txt` files from the source
   directories above.

   **They live in `assets/`, not `res/raw/`, on purpose.** Release builds run with
   `isShrinkResources = true`, and a `res/raw` entry that no code references is exactly what the
   shrinker removes — the APK would have shipped the fonts with their licences stripped out. Assets
   are never shrunk and never reported as unused resources, so no keep rule or lint suppression is
   needed to hold them in place.
2. **The fonts are not sold on their own.** They are not; they are part of the app.
3. **Reserved Font Names may not be reused.** Neither font declares one, so a modified copy could
   keep the name — but we ship them unmodified, so this does not arise.

A licences screen that surfaces these to the user is post-v1; shipping the texts in the APK already
satisfies the OFL.

### What was deliberately not bundled

- **DM Sans Italic** (`DMSans-Italic[opsz,wght].ttf`, 285 KB). The prototype's `@import` loads
  italic 400, but nothing in `App.tsx` uses italic anywhere — verified by search. Bundling it would
  add 285 KB to every APK for a face no screen renders. Android synthesises a slant if italic is
  ever asked for. If a real italic is wanted later, add the file and one `Font(...)` entry.
- **DM Sans static instances.** They no longer exist upstream; DM Sans is variable-only as of
  version 4.x. One 240 KB file covers `wght` 100–1000, which is smaller than the five static faces
  it replaces.

---

## Images

None yet. The Figma prototype carries four PNGs that `App.tsx` never references; fetching them is
`Plan.md` M2.9, deliberately deferred until a screen needs one. `ATTRIBUTIONS.md` from the Make
file must be fetched with them, and its contents folded into this file before any of them ships.
