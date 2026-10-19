package com.cristianprincipi.habits.core.designsystem.component

import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.ReadingInput
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/**
 * The number dialog's state — M16.2b, ADR-156 §2: *"Weight · Thu 15 Oct"*, a field, Save / Cancel,
 * and Clear when the day has a value.
 *
 * Held by a ViewModel rather than the composable, as the Sync screen's disconnect prompt is, so what
 * Save does with a given text is a JVM test rather than a device one. Two hold one: Week's, for a
 * tapped cell, and the number widget's (M16.3), for today.
 *
 * @param measureName and [unit] as the card draws them when the dialog opened.
 * @param hadValue whether the day had a reading when the dialog opened — which is what offers Clear.
 * @param saveFailed the last Save did not land and the dialog is still open, so the field says so.
 *   Only the widget's dialog sets it: Week closes first and says it in a snackbar with a retry.
 */
data class ReadingEntry(
    val measureId: MeasureId,
    val measureName: String,
    val unit: String,
    val date: LocalDate,
    val text: String,
    val hadValue: Boolean,
    val saveFailed: Boolean = false,
) {
    /** What Save would write, or `null` while the field holds something that is not a number. */
    val value: Double? get() = ReadingInput.parse(text)

    /** Save is disabled, not refused, on anything that is not a number — the user sees why. */
    val canSave: Boolean get() = value != null

    /**
     * The field holds text that is not a number. Empty is not an error — it is where a day without a
     * value starts, and saying "not a number" before anything is typed would be scolding.
     */
    val isInvalid: Boolean get() = text.isNotBlank() && value == null
}

/**
 * A stored value as the dialog's field starts: every digit it has, no grouping, in the reader's
 * decimal separator when that is a comma — `ReadingInput` reads either back.
 *
 * ⚠ Built from `Double.toString` rather than a `NumberFormat`, so the digits are always ASCII and the
 * separator always one the parser accepts. A locale whose formatter used other digits or another
 * separator would otherwise prefill text that its own Save could not read.
 */
fun prefillText(
    value: Double?,
    locale: Locale,
): String {
    val plain = value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: return ""
    val usesComma = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
    return if (usesComma) plain.replace('.', ',') else plain
}
