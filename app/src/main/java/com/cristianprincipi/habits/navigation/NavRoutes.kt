package com.cristianprincipi.habits.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app, as a type-safe Navigation Compose route (`CLAUDE.md` §4).
 *
 * Routes are `@Serializable` objects rather than string constants, so `navigate(Week)` and
 * `composable<Week> { }` are checked by the compiler and a typo cannot become a runtime crash on a
 * screen nobody opened during testing.
 *
 * **None of them carry arguments, and that is a decision rather than an omission.** ADR-010 removed
 * habit create, rename and delete — a habit is a *column* in the user's Notion schema, and this app
 * does not edit their schema. So there is no per-habit destination to address, and
 * [ManageHabits] is a list rather than something reached with an id (M1.10, confirmed by the user;
 * the route was called `HabitEdit(habitId: String?)` before that). Add a parameterised route when a
 * screen genuinely needs one; do not add one in anticipation.
 *
 * ⚠ **The connection wizard is deliberately not a route.** Setup is a **gate around** the graph
 * rather than a tab (M12.9's rule: a first launch with no configuration goes straight to the
 * wizard), so `HabitsNavHost` shows it with an `if` and the graph below never sees it.
 *
 * ⭐ **[SyncStatus] is a route, and that was the user's call in M5.2.** Deleting `HabitsAppRoot`
 * took away the only thing pointing at that screen, and it would otherwise have been unreachable
 * until M7 builds Settings. It is a destination without a tab, reached from Settings.
 */
sealed interface NavRoute

/**
 * The five destinations with a bottom-nav tab, in the order the design shows them
 * (`docs/design-spec.md` §1).
 *
 * Being a separate type is what keeps [ManageHabits] out of the navigation bar: the bar is built
 * from this type, so adding a sixth tab has to be a deliberate act rather than a side effect of
 * declaring a route. Each tab also owns a colour and an icon, but those live with the bar in M5.2 —
 * a route is a destination, not a piece of styling.
 */
sealed interface TopLevelRoute : NavRoute

/** Monthly completion density for every habit at once — `docs/design-spec.md` §2. */
@Serializable
data object Month : TopLevelRoute

/**
 * The landing tab, and the home of the check-off loop (M6).
 *
 * **The start destination is this one, not [Month]** — the user's decision. Opening the app lands on
 * the current week with today already ringed, which is how v1 gets a "today" experience without a
 * sixth tab. System back therefore exits from here and returns here from any other tab
 * (`docs/design-spec.md` §1, `CLAUDE.md` §1).
 */
@Serializable
data object Week : TopLevelRoute

/** A year of completions as a dot grid — `docs/design-spec.md` §4. */
@Serializable
data object Year : TopLevelRoute

/** Completion rates, streaks and the year heatmap — `docs/design-spec.md` §5. */
@Serializable
data object Stats : TopLevelRoute

/** Notion connection, sync state and everything about the app — `docs/design-spec.md` §6. */
@Serializable
data object Settings : TopLevelRoute

/**
 * The habit list — **read-only**, by design.
 *
 * Not a tab, and not named `HabitEdit`. ADR-010 makes habits a projection of the user's Notion
 * schema, so this screen links out to Notion and offers app-local colour controls rather than
 * editing anything (M7.5). The old name would invite a future session to build the editing that
 * must not exist.
 */
@Serializable
data object ManageHabits : NavRoute

/**
 * The mapping, editable without disconnecting — `feature/notionsetup`, M7.8.
 *
 * Not a tab, and reached from two places for two different reasons: from [ManageHabits], where a
 * user goes to adopt a column they just added in Notion; and from [SyncStatus], where a **deleted**
 * column has halted the sync and this is the only thing that will restart it (ADR-079).
 *
 * It carries no argument. There is one mapping, and the screen reads it — a route that took a data
 * source id would let a caller open the mapping for a database the app is not connected to.
 */
@Serializable
data object EditMapping : NavRoute

/**
 * Notion connection health, last sync, manual retry and disconnect — `feature/syncstatus`.
 *
 * **A destination, not a tab.** The design has five tabs and this is not one of them; it hangs off
 * Settings, which is where a connection belongs (`docs/design-spec.md` §6). Until M7 builds that
 * screen, `HabitsNavHost` carries a single row into here — see `SettingsScaffolding`.
 */
@Serializable
data object SyncStatus : NavRoute

/**
 * Version, what leaves the device, and the three licences the APK ships — `feature/about`, M11.8.
 *
 * **A destination, not a tab**, reached from [Settings] like [SyncStatus] beside it. It takes no
 * argument and observes nothing: everything on it is fixed at build time.
 */
@Serializable
data object About : NavRoute
