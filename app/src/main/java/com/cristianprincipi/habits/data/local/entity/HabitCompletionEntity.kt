package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One habit, checked off on one day.
 *
 * **The existence of the row is the fact.** There is no `completed` column: a day with a row was
 * done, a day without one was not. Storing misses would mean a row per habit per day for every day
 * nobody did anything, which is most of them.
 *
 * The composite primary key is the uniqueness constraint `Plan.md` M3.3 asks for — checking the same
 * habit off twice on the same day cannot produce two rows, whatever the caller does.
 *
 * Both foreign keys cascade. Habits are deleted locally only when the database is wiped on
 * disconnect (`CLAUDE.md` §3.6); the day cascade stops a completion outliving the day it belongs to.
 */
@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayEntity::class,
            parentColumns = ["epochDay"],
            childColumns = ["epochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // habitId leads the primary key and is indexed by it; epochDay needs its own, both for the
    // foreign key Room requires an index for, and because every screen queries a range of dates
    // across all habits at once.
    indices = [Index(value = ["epochDay"])],
)
data class HabitCompletionEntity(
    val habitId: String,
    val epochDay: Long,
)
