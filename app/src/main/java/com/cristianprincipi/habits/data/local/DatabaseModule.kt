package com.cristianprincipi.habits.data.local

import android.content.Context
import androidx.room.Room
import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Builds the database and hands out its DAOs.
 *
 * **No `fallbackToDestructiveMigration`, here or anywhere.** `CLAUDE.md` §6 allows it only in a
 * debug build, and this project has no use even for that: a habit's local history can be the only
 * surviving copy once its Notion property is deleted (ADR-010), so a schema change that cannot
 * migrate should fail loudly in development rather than quietly wipe the user's history in release.
 *
 * Foreign keys are left to Room, which turns them on for every connection it opens. That is what
 * makes `habit_completions`' key to `days` a real constraint rather than documentation.
 *
 * ⚠⚠ **[ALL_MIGRATIONS] is the same array the migration test runs**, and that is deliberate: a
 * migration that is written but never added here fails on a user's device and nowhere else — every
 * test would pass, because a test that builds the database from scratch never migrates anything.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // SpreadOperator: detekt is right that `*` copies the array, and wrong that it matters here —
    // one array of one element, copied once, at the moment the app builds its database. The
    // alternative is listing each migration at this call site, which is precisely the drift the
    // KDoc above exists to prevent: the builder and the migration test must read the same list, or
    // a migration can be written, tested, and never wired up.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HabitsDatabase =
        Room
            .databaseBuilder(context, HabitsDatabase::class.java, HabitsDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideHabitDao(database: HabitsDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideDayDao(database: HabitsDatabase): DayDao = database.dayDao()

    @Provides
    fun provideCompletionDao(database: HabitsDatabase): CompletionDao = database.completionDao()

    @Provides
    fun provideMeasureDao(database: HabitsDatabase): MeasureDao = database.measureDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: HabitsDatabase): TransactionRunner = RoomTransactionRunner(database)
}
