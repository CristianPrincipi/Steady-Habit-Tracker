package com.cristianprincipi.habits.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change this database has ever had.
 *
 * ### ⚠⚠ There is no `fallbackToDestructiveMigration`, here or anywhere, and there must not be
 *
 * `DatabaseModule` states the reason and it is the strongest one this app has: a habit's local
 * history can be the **only surviving copy**. Deleting a property in Notion destroys its column and
 * every value in it (`docs/notion-schema.md` §4.1), and ADR-081 keeps the rows precisely so that
 * history survives the column. A destructive fallback would throw away the thing the rest of the
 * design goes to some length to protect — quietly, in release, on a user's device.
 *
 * ### The DDL is copied from Room's own exported schema, not written by hand
 *
 * `MigrationTestHelper.runMigrationsAndValidate` compares the migrated database against the JSON
 * under `app/schemas/`, column for column and index for index. Hand-written SQL that is merely
 * *equivalent* — a different column order, a missing `IF NOT EXISTS`, an omitted
 * `ON UPDATE NO ACTION` — fails that comparison, and the failure message is a wall of schema. So
 * each statement below is the `createSql` from the target version's JSON with `${'$'}{TABLE_NAME}`
 * substituted, and nothing else.
 */
internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        /**
         * M15.4: `Weight` and `Sleep` — number columns the user asked to see averaged.
         *
         * ⭐ **Purely additive: two new tables and their indices, and not one existing row is read
         * or written.** That is worth saying because it is what makes this migration safe to the
         * point of dullness — an interrupted run leaves a version-1 database, which the next launch
         * migrates again. Every habit, day and completion is untouched by construction.
         *
         * ⚠ **`measurements` has foreign keys to both `measures` and `days`**, so the order of
         * these statements matters: SQLite will accept a table referencing one that does not exist
         * yet, but only because it does not check until the constraint is used. Creating the parent
         * first costs nothing and removes the question.
         */
        override fun migrate(connection: SupportSQLiteDatabase) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `measures` (" +
                    "`id` TEXT NOT NULL, " +
                    "`notionPropertyId` TEXT NOT NULL, " +
                    "`notionPropertyName` TEXT NOT NULL, " +
                    "`unit` TEXT NOT NULL, " +
                    "`color` TEXT NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_measures_notionPropertyId` " +
                    "ON `measures` (`notionPropertyId`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `measurements` (" +
                    "`measureId` TEXT NOT NULL, " +
                    "`epochDay` INTEGER NOT NULL, " +
                    "`value` REAL NOT NULL, " +
                    "PRIMARY KEY(`measureId`, `epochDay`), " +
                    "FOREIGN KEY(`measureId`) REFERENCES `measures`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`epochDay`) REFERENCES `days`(`epochDay`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_measurements_epochDay` ON `measurements` (`epochDay`)",
            )
        }
    }

// MagicNumber: a migration is named by the two versions it joins; a constant called THREE would say less.
@Suppress("MagicNumber")
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        /**
         * M16.2: `measurement_edits` — a number typed on this device that Notion has not been sent
         * yet, with what Notion held when it was typed (ADR-156).
         *
         * ⭐ **Additive again: one new table and its index, and no existing row is read or
         * written**, so `MIGRATION_1_2`'s safety argument holds unchanged. An upgraded install starts
         * owing nothing, which is true — before this version nothing could be typed.
         *
         * Both parents (`measures`, `days`) exist since version 2, so there is no ordering question.
         */
        override fun migrate(connection: SupportSQLiteDatabase) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `measurement_edits` (" +
                    "`measureId` TEXT NOT NULL, " +
                    "`epochDay` INTEGER NOT NULL, " +
                    "`baseValue` REAL, " +
                    "`editedAtEpochMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`measureId`, `epochDay`), " +
                    "FOREIGN KEY(`measureId`) REFERENCES `measures`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`epochDay`) REFERENCES `days`(`epochDay`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_measurement_edits_epochDay` ON `measurement_edits` (`epochDay`)",
            )
        }
    }

/**
 * Handed to `Room.databaseBuilder`, and to the migration test, from one place.
 *
 * ⚠ **A migration that exists but is never added is the same as no migration at all** — the app
 * throws `IllegalStateException` on the user's device and nowhere else. One list, read by both the
 * builder and the test, is what makes "written" and "wired up" the same event.
 */
internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
