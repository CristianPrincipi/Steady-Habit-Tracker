package com.cristianprincipi.habits.feature.settings

import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary

/**
 * What the Settings screen renders.
 *
 * **One field, and that is the honest size.** Every preference the plan once expected on this
 * screen has been decided away — theme mode (dark-only, ADR-009), week start (a constant, M3.7),
 * export/import (post-v1), reminders (post-v1), and, on 2026-09-04, sync cadence and unmetered-only
 * as well (M11.10, the user's ruling: 15 minutes is WorkManager's own floor, so a picker could only
 * make sync slower). `SettingsRepository`'s KDoc has said the same thing since M3.
 *
 * So what is left of "settings" in this app is the Notion connection — which is why the one thing
 * this screen knows is [connection], and why everything else on it is a way to somewhere else.
 */
data class SettingsUiState(
    val connection: ConnectionSummary = ConnectionSummary(),
)
