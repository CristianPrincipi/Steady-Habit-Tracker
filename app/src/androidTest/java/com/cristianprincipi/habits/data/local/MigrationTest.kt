package com.cristianprincipi.habits.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's schema migrations, run against a real SQLite file at the old version.
 *
 * ### ⭐⭐ What this protects, and why no other test in the project could
 *
 * Every other test here builds the database **from scratch**, at the current version, and therefore
 * migrates nothing. All of them would pass against a migration that was never written, never added
 * to [ALL_MIGRATIONS], or written wrongly — and the failure would appear for the first time on a
 * device that already had the app installed. Which is every real user, and no test device.
 *
 * `CLAUDE.md` §6: "All schema changes ship with a migration + a migration test."
 *
 * ### The two assertions are different things, and both are needed
 *
 * `runMigrationsAndValidate` proves the **shape**: it compares the migrated database against the
 * committed JSON under `app/schemas/`, column for column and index for index, so SQL that is merely
 * *equivalent* fails here rather than at runtime. [everyRowSurvivesTheFirstMigration] proves the
 * **data**, which validation says nothing about — a migration that dropped `habits` and recreated
 * it would validate perfectly.
 *
 * ⚠ The schema JSON reaches the device inside the androidTest APK, put there by the Room Gradle
 * plugin with no `sourceSets` configuration of ours. Verified by unzipping the built APK, not
 * assumed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HabitsDatabase::class.java,
        )

    @Test
    fun theSchemaMigratesFromOneToTheCurrentVersion() {
        helper.createDatabase(TEST_DB, 1).close()

        // ⭐ `validateDroppedTables = true`: a migration that left an orphaned table behind would
        // otherwise pass, and the leftovers of a botched migration are what a later one trips over.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS)

        assertEquals(2, migrated.version)
        migrated.close()
    }

    @Test
    fun everyRowSurvivesTheFirstMigration() {
        // ⚠⚠ **The assertion that matters, and validation does not make it.** ADR-010 leaves this
        // app no way to recreate a habit's history — deleting a Notion property destroys its column
        // and every value in it, so these rows can be the only surviving copy. A migration that
        // dropped and recreated `habits` would validate cleanly and lose a year.
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO habits " +
                    "(id, notionPropertyId, notionPropertyName, color, startDateEpochDay, isActive, sortOrder) " +
                    "VALUES ('h1', 'ZI%40W', '📖 Read', 'BLUE', 20000, 1, 0)",
            )
            old.execSQL(
                "INSERT INTO days " +
                    "(epochDay, notionPageId, localUpdatedAtEpochMillis, " +
                    "remoteLastEditedAtEpochMillis, syncState) " +
                    "VALUES (20300, 'page-1', NULL, 1725000000000, 'SYNCED')",
            )
            old.execSQL("INSERT INTO habit_completions (habitId, epochDay) VALUES ('h1', 20300)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS).use { migrated ->
            migrated.query("SELECT notionPropertyName, color FROM habits WHERE id = 'h1'").use { cursor ->
                assertTrue("The habit did not survive the migration", cursor.moveToFirst())
                // ⚠ The name is read back, not just counted. `docs/notion-schema.md` §2.3 requires
                // these stored verbatim, emoji and all, and a migration that round-tripped them
                // through anything lossy would show up here and nowhere else.
                assertEquals("📖 Read", cursor.getString(0))
                assertEquals("BLUE", cursor.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM habit_completions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            // And the new tables exist and are empty. The migration is additive, so there is
            // nothing for it to have invented.
            migrated.query("SELECT COUNT(*) FROM measurements").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        /** Not `HabitsDatabase.NAME`: a test must never open the file the app itself is using. */
        const val TEST_DB = "migration-test.db"
    }
}
