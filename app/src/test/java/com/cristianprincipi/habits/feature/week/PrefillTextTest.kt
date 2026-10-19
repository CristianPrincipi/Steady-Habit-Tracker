package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.core.designsystem.component.prefillText
import com.cristianprincipi.habits.domain.model.ReadingInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * [prefillText] — the dialog's field as it opens on a day with a value (M16.2b).
 *
 * ⭐ The property that matters is the last test: **whatever this writes, Save can read back as the
 * same number.** A prefill that did not survive the parser would make an untouched Save a refusal,
 * or worse, a different value.
 */
class PrefillTextTest {
    @Test
    fun theStoredValueIsShownWhole() {
        // Not the cell's one-decimal rounding: an untouched Save must not change the value.
        assertEquals("82.95", prefillText(82.95, Locale.US))
    }

    @Test
    fun aWholeNumberHasNoTrailingZero() {
        assertEquals("84", prefillText(84.0, Locale.US))
    }

    @Test
    fun aCommaLocaleGetsAComma() {
        assertEquals("82,9", prefillText(82.9, Locale.ITALY))
        assertEquals("82,9", prefillText(82.9, Locale.GERMANY))
    }

    @Test
    fun thereIsNeverAGroupingSeparator() {
        assertEquals("1234.5", prefillText(1234.5, Locale.US))
        assertEquals("1234,5", prefillText(1234.5, Locale.ITALY))
    }

    @Test
    fun aDayWithoutAValueStartsEmpty() {
        assertEquals("", prefillText(null, Locale.US))
    }

    @Test
    fun everyPrefillReadsBackAsTheSameNumber() {
        val locales = listOf(Locale.US, Locale.ITALY, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG"))
        val values = listOf(82.9, 84.0, 7.25, 0.1, 1234.5, 1e-4, 12_345_678.9, -1.5)
        locales.forEach { locale ->
            values.forEach { value ->
                assertEquals("$value in $locale", value, ReadingInput.parse(prefillText(value, locale)))
            }
        }
    }
}
