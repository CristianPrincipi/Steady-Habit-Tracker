package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One reading of one measure on one day — 84.0 kg on the 7th of September (M15.4).
 *
 * ### ⚠⚠ The row's existence is the fact, exactly as it is for [HabitCompletionEntity]
 *
 * A day with no row had **no reading**, and that is not the same as a reading of zero. The
 * distinction is the one thing in this table that can go wrong invisibly: a missed weigh-in stored
 * as `0.0` would drag an average down without anything on screen looking unwell, and the user would
 * have no way to tell a wrong average from a true one.
 *
 * Notion says so too. `NotionPropertyValue.Empty` is its own case — *"the property is on the page
 * but holds nothing"* — and the sync must skip it rather than decode it as a number.
 *
 * ### Both foreign keys cascade, for [HabitCompletionEntity]'s reasons
 *
 * Measures are deleted locally only when the database is wiped on disconnect; the day cascade stops
 * a reading outliving the day it belongs to.
 */
@Entity(
    tableName = "measurements",
    primaryKeys = ["measureId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = MeasureEntity::class,
            parentColumns = ["id"],
            childColumns = ["measureId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayEntity::class,
            parentColumns = ["epochDay"],
            childColumns = ["epochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // measureId leads the primary key and is indexed by it; epochDay needs its own, both for the
    // foreign key Room requires an index for, and because the average queries a range of dates.
    indices = [Index(value = ["epochDay"])],
)
data class MeasurementEntity(
    val measureId: String,
    val epochDay: Long,
    /**
     * The reading. A `Double` because Notion sends a bare JSON number — `84`, not the `84.00` its
     * display formatting shows (`docs/notion-schema.md` §2.4). Never parsed from a display string.
     */
    val value: Double,
)
