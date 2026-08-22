package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.DAYS_PER_YEAR_WINDOW
import com.cristianprincipi.habits.domain.model.CompletionRate
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * What share of the eligible days a habit was kept (`docs/design-spec.md` §4).
 *
 * **Eligible = the trailing 365 days ending today, from the habit's start date onward.** Both ends
 * inclusive, so a habit that started today has one eligible day rather than none.
 *
 * `Plan.md` M3.10 words this as "completions ÷ days from startDate to today", which is the same
 * number for any habit younger than a year and differs only for older ones. The design spec's
 * version wins (`CLAUDE.md` §1: the Figma design specifies what the app does), and it is also the
 * one that agrees with the Year grid drawn directly above the figure — a percentage measured over a
 * different window than the dots beside it would simply look wrong.
 */
class GetCompletionRate
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        operator fun invoke(
            completedDates: Collection<LocalDate>,
            startDate: LocalDate,
        ): CompletionRate {
            val today = LocalDate.now(clock)
            val windowStart = today.minusDays((DAYS_PER_YEAR_WINDOW - 1).toLong())
            val eligibleFrom = maxOf(startDate, windowStart)

            // A habit whose start date is in the future has nothing to have missed yet. The spec
            // asks for the divide-by-zero to be guarded; CompletionRate.percent is where that
            // happens, and this is the case that reaches it.
            if (eligibleFrom > today) return CompletionRate(completedDays = 0, eligibleDays = 0)

            val eligible = ChronoUnit.DAYS.between(eligibleFrom, today).toInt() + 1
            val window = eligibleFrom..today
            return CompletionRate(
                completedDays = completedDates.toSet().count { it in window },
                eligibleDays = eligible,
            )
        }
    }
