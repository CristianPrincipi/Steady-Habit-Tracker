package com.cristianprincipi.habits.data.local

/**
 * Runs the block, and nothing else.
 *
 * **It cannot roll back**, so it proves ordering and effects but says nothing about atomicity.
 * Whether a failed `setCompleted` leaves a day row behind is a question only the real SQLite
 * transaction can answer, and M3.11 is where it gets asked.
 */
class TestTransactionRunner : TransactionRunner {
    override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
}
