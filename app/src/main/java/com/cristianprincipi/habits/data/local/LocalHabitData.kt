package com.cristianprincipi.habits.data.local

import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.HabitDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The synced habit data, as one thing that can be removed.
 *
 * Extracted because the alternative was worse: `NotionSetupRepositoryImpl` had grown three DAOs and
 * a transaction runner purely so that **one branch of disconnect** could wipe the database, which
 * made a class about talking to Notion also a class about local storage. Detekt noticed the
 * parameter count; the parameter count was noticing the second job.
 *
 * **There is exactly one caller and there should stay exactly one.** Nothing else in this app
 * deletes a habit — ADR-010 removed habit deletion, and a sync deactivates rather than deletes so
 * that history survives a column leaving the mapping. This exists only for the disconnect action
 * `CLAUDE.md` §3.6 requires, where the user has explicitly asked for their data to be removed.
 */
@Singleton
class LocalHabitData
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val dayDao: DayDao,
        private val completionDao: CompletionDao,
        private val transactionRunner: TransactionRunner,
    ) {
        /**
         * Every habit, day and check-off, in one transaction.
         *
         * **Children first**, even though both foreign keys cascade. A delete that depends on a
         * pragma being enabled to be complete is not the thing to lean on at the moment a user has
         * asked for their data to be gone — and the explicit order costs one line.
         *
         * One transaction because three half-applied deletes would leave days whose habits are gone.
         */
        suspend fun deleteEverything() {
            transactionRunner.withTransaction {
                completionDao.deleteAll()
                dayDao.deleteAll()
                habitDao.deleteAll()
            }
        }
    }
