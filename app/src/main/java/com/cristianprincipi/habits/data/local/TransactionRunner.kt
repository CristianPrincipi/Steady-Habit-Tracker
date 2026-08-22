package com.cristianprincipi.habits.data.local

import androidx.room.withTransaction

/**
 * Runs a block of writes as one atomic database transaction.
 *
 * **This exists because a check-off spans two tables.** `HabitRepositoryImpl.setCompleted` upserts
 * the day and writes the completion together — the completion has a foreign key to `days`, so a
 * half-applied write is either a constraint failure or a day row claiming a check-off it does not
 * have. Room's own `withTransaction` is an extension on `RoomDatabase`, which cannot be faked, so
 * naming the capability as an interface is what lets the repository be unit tested on the JVM
 * instead of only on an emulator (ADR-017 — Room DAO tests are instrumented here).
 */
interface TransactionRunner {
    suspend fun <R> withTransaction(block: suspend () -> R): R
}

/** The real one. The only implementation outside the test source set. */
internal class RoomTransactionRunner(
    private val database: HabitsDatabase,
) : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R = database.withTransaction(block)
}
