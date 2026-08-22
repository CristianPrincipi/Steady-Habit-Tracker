package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Streak
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Current and best streak for one habit (`docs/design-spec.md` §5.3).
 *
 * **Today counts as still in progress.** If today has not been checked off, the current streak is
 * measured from yesterday rather than reset to zero — otherwise every user would open the app each
 * morning to a streak of 0 and a habit kept for a year would look abandoned before breakfast. This
 * matches the prototype's own `calcCurrentStreak`, which steps back a day before it starts counting.
 * The design spec's phrase "consecutive days ending today" (§5.3) is a loose paraphrase of that
 * code, not a stricter rule.
 *
 * **Any missed day breaks a streak.** Schedules are deferred post-v1, so every habit is daily and
 * there is no "gap on a non-scheduled day" case (`CLAUDE.md` §1). Do not add one.
 *
 * **Both figures are measured over the whole history**, which is a deliberate departure from the
 * prototype. It bounds the best streak to the trailing 365 days while letting the current streak run
 * back indefinitely, so a habit kept for more than a year reports a current streak longer than its
 * best — a pair of numbers [Streak] rejects by invariant, and rightly. Measuring both over the same
 * data makes `best >= current` true by construction rather than by luck.
 */
class CalculateStreak
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        operator fun invoke(completedDates: Collection<LocalDate>): Streak {
            val today = LocalDate.now(clock)
            // Days that have not happened yet cannot be part of a run. Nothing in the app can
            // create one — ToggleCompletion refuses a future date — but a sync pulling a row from a
            // device whose clock was ahead can, and it would otherwise inflate the best streak.
            val dates = completedDates.filterTo(mutableSetOf()) { it <= today }
            return Streak(current = currentRun(dates, today), best = bestRun(dates))
        }

        private fun currentRun(
            dates: Set<LocalDate>,
            today: LocalDate,
        ): Int {
            var cursor = if (today in dates) today else today.minusDays(1)
            var length = 0
            while (cursor in dates) {
                length++
                cursor = cursor.minusDays(1)
            }
            return length
        }

        private fun bestRun(dates: Set<LocalDate>): Int {
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            dates.sorted().forEach { date ->
                run = if (previous?.plusDays(1) == date) run + 1 else 1
                if (run > best) best = run
                previous = date
            }
            return best
        }
    }
