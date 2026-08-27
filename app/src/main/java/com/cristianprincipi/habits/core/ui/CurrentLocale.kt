package com.cristianprincipi.habits.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * The locale to format dates in, as a `java.util.Locale` that `core/datetime` can take.
 *
 * ### ⚠ Two obvious ways to write this are both wrong, and Lint says so
 *
 * `LocalConfiguration.current.locales[0]` reads a `Configuration` field composition does not
 * observe, and `Locale.getDefault()` is read once and then goes stale for the life of the process.
 * Android Lint's `NonObservableLocale` rejects both. `androidx.compose.ui.text.intl.Locale.current`
 * is state-backed, so a label actually changes when the user changes their language.
 *
 * It is a function rather than a comment repeated at each call site because the mistake is easy,
 * silent, and only caught by a Lint rule most people have never heard of. Every screen that formats
 * a date should reach for this.
 */
@Composable
fun rememberCurrentLocale(): Locale {
    val composeLocale = ComposeLocale.current
    return remember(composeLocale) { Locale.forLanguageTag(composeLocale.toLanguageTag()) }
}
