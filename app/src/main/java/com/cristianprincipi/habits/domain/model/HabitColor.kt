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
 * These five are the whole palette a habit can be assigned. The design's other two colours are
 * spoken for: amber is `chart-5` and grey is the Settings tab (`docs/tokens.md \§1`).
 *
 * ### ⚠ [RED] is ours, not the prototype's — M15.2
 *
 * The other four are ported (`docs/tokens.md §1.3`); red is an addition the user asked for, and it
 * is the only colour here the design does not contain. It exists because **four colours and five
 * habits made two of them match**: `HabitImporter.defaultColorAt` cycles this enum with `%`, so the
 * fifth habit wrapped round to blue — which is what the user was looking at on their own phone,
 * Focus and Read both blue. Five entries is what retires that, and it is exact rather than
 * comfortable: a **sixth** habit would wrap again.
 *
 * ⚠ **Weight and Sleep reuse `GREEN` and `PURPLE`** (ADR-121 §9) and are deliberately *not* habits,
 * so they take no entry here. A measure never shares a list, grid or widget with a habit, which is
 * why the reuse cannot collide on screen. Do not "fix" it by adding entries for them.
 */
enum class HabitColor {
    BLUE,
    GREEN,
    ORANGE,
    PURPLE,
    RED,
}
