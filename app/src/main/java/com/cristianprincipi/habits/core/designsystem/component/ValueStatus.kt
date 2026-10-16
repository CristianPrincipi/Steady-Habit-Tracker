package com.cristianprincipi.habits.core.designsystem.component

/**
 * What one cell of a [WeekValues] row is showing.
 *
 * Three cases, not a nullable number, because the design system does no formatting — the caller
 * hands over finished text — and "no value yet" and "no value at all" draw differently.
 */
enum class ValueStatus {
    /** A reading exists; its text is drawn at full strength. */
    RECORDED,

    /** A day that has happened with no reading: the caller's dash, dimmed. Never a zero. */
    MISSING,

    /** A day that has not happened: an empty, dimmed cell, the way a future pill is dimmed. */
    FUTURE,
}
