package com.cristianprincipi.habits.core.designsystem.component

/*
 * Putting a habit's two display halves back together — the other end of M7.2's split.
 *
 * `HabitMapper.splitEmojiAndName` takes a Notion property name apart into an emoji and a name, and
 * it is explicit that **the emoji half may be empty**: a checkbox column called plainly `Meditate`
 * is a perfectly ordinary thing for the user to add, and `aNameWithNoEmojiKeepsAllOfItself` pins
 * that the whole string then becomes the name.
 *
 * ⚠ **Every one of the five habits in `docs/notion-schema.md` §2.3 happens to carry an emoji**, and
 * so does every preview, gallery entry and test fixture in this repo. That is precisely why the
 * empty case is easy to write past: it looks right in every screenshot anyone has ever taken of
 * this app. Both functions here exist so the rule is written once, in a pure function a JVM test can
 * hold, rather than re-derived at each call site.
 */

/**
 * A habit's emoji and name as one line, for a chip or a sentence.
 *
 * A plain `"$emoji $name"` renders an emoji-less habit as `" Meditate"` — indented by one space
 * against every neighbour that has an icon.
 */
internal fun habitLabel(
    emoji: String,
    name: String,
): String = if (emoji.isEmpty()) name else "$emoji $name"

/**
 * The single glyph that goes inside a habit's tinted circle — its emoji, or the initial standing in
 * for one.
 *
 * **The user's call at M7.2.** Without a fallback the circle draws empty, and this repo has already
 * ruled once that a drawn-but-blank element reads as something that failed to load rather than as
 * deliberate quiet: ADR-027 filled the habit card's empty description slot for exactly that reason.
 * An initial is the convention people already read as "no picture set", it always fills the circle,
 * and the circle keeps its size so nothing around it reflows.
 *
 * ⚠ **By code point, not by `first()`.** A name beginning with an astral character — an emoji that
 * did not make it into the emoji half because it had no space after it, say — would give
 * `first()` half a surrogate pair and render a replacement box.
 *
 * ⚠ **`uppercase()` with no locale**, which in Kotlin is the root locale. A locale-sensitive
 * uppercase would turn a Turkish `i` into `İ`, which is correct Turkish and the wrong letter to put
 * in a circle standing for a name the user typed in English.
 *
 * @return an empty string only when there is nothing at all to draw, which needs a property named
 *   with whitespace — the circle then falls back to being a plain coloured dot.
 */
internal fun habitGlyph(
    emoji: String,
    name: String,
): String =
    when {
        emoji.isNotEmpty() -> emoji
        name.isBlank() -> ""
        else -> String(Character.toChars(name.trimStart().codePointAt(0))).uppercase()
    }
