package com.cristianprincipi.habits.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The app's settings \— which in v1 is almost nothing, and that is not an oversight.
 *
 * Every preference the plan once expected here has been decided away. Theme mode: v1 is dark-only
 * (ADR-009). Reminders: deferred post-v1. Week start: hardcoded Monday, a constant in
 * `core/datetime` rather than a preference, matching the prototype. Onboarding-seen: there is no
 * onboarding. What remains of "settings" in this app is the Notion connection, and that is a
 * different shape of thing.
 *
 * **The Notion settings are deliberately absent until M4.** The property mapping is the obvious
 * candidate for this interface, but it cannot be typed honestly yet: `docs/notion-schema.md \§6.1`
 * says to key the mapping on each property's **id** rather than its name, and whether those ids are
 * stable is a question M4.1 answers against the live API. Inventing the type now would mean
 * inventing it twice. The same goes for the sync cursor, whose form follows the query endpoint.
 *
 * Which leaves one value that is certainly ours and certainly this shape.
 */
interface SettingsRepository {
    /**
     * When the last **successful** sync finished, or `null` if none ever has.
     *
     * Drives the "last synced" line on Settings. A failed sync leaves this untouched \— the user needs
     * to know when the data was last actually correct, not when something last tried.
     */
    fun observeLastSyncedAt(): Flow<Instant?>

    /** Records a successful sync. Called by the sync engine (M4), by nothing else. */
    suspend fun setLastSyncedAt(at: Instant)
}
