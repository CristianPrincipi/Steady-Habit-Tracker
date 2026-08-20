package com.cristianprincipi.habits.core.testing

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider

/**
 * Builds a throwaway in-memory Room database for a test.
 *
 * In-memory means the database is destroyed with the process, so tests cannot leak state into one
 * another through a file on disk — the classic source of DAO tests that pass alone and fail in a
 * suite.
 *
 * `allowMainThreadQueries()` is set because a test asserting on a DAO has no UI to keep responsive.
 * It is a **test-only** relaxation: production code must never call it, and every DAO the UI
 * observes returns a `Flow` anyway (`CLAUDE.md` §6).
 */
inline fun <reified T : RoomDatabase> createInMemoryDatabase(
    context: Context = ApplicationProvider.getApplicationContext(),
): T =
    Room
        .inMemoryDatabaseBuilder(context, T::class.java)
        .allowMainThreadQueries()
        .build()
