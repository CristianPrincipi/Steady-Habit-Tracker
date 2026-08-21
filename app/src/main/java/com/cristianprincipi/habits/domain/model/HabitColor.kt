package com.cristianprincipi.habits.domain.model

/**
 * The colour a habit is drawn in.
 *
 * **App-local.** The user's Notion database has no colour property and never will while ADR-010
 * stands, so this is assigned here and stored here. The consequence is stated in
 * `docs/notion-schema.md §3`: a fresh install on a new device starts from the default assignment
 * until the user re-picks.
 *
 * An enum rather than a Compose `Color` so the domain layer stays free of Android and of the
 * design system. The UI resolves it against `HabitsTheme.colors.habit.*`.
 *
 * These four are the whole palette a habit can be assigned. The design's other two colours are
 * spoken for: amber is `chart-5` and grey is the Settings tab (`docs/tokens.md \u00a71`).
 */
enum class HabitColor {
    BLUE,
    GREEN,
    ORANGE,
    PURPLE,
}
