package com.cristianprincipi.habits.feature.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntry
import com.cristianprincipi.habits.core.designsystem.component.ReadingEntryDialog
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.rememberCurrentLocale
import com.cristianprincipi.habits.domain.model.MeasureId
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.util.Locale

/**
 * The number widget's dialog, drawn **over the home screen** — M16.3.
 *
 * A widget cannot hold a text field, so its square opens this: Week's own dialog for today, in a
 * window of its own with nothing behind it but the launcher. Save, Clear, Cancel or back and it is
 * gone, and the home screen is where the user was.
 *
 * ⚠⚠ **Its manifest entry is what keeps the app out of the way**, and each attribute is load-bearing:
 * a translucent theme (the launcher shows through), `taskAffinity=""` with `excludeFromRecents` (its
 * own task, so finishing returns to the home screen rather than surfacing the app's task behind it),
 * and **not exported** — the widget's `PendingIntent` is this app's own, and nothing else has a reason
 * to open it.
 *
 * ⚠ **`CLEAR_TASK` with `NEW_TASK`**, because a dialog left open (the user pressed home) would
 * otherwise be brought back as it was — for whichever number it was opened for — when the user taps
 * a different number's square.
 */
@AndroidEntryPoint
class TodayReadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getStringExtra(EXTRA_MEASURE_ID).isNullOrBlank()) {
            // Not opened by a widget's square: there is no number to ask about.
            finish()
            return
        }
        setContent {
            HabitsTheme {
                TodayReadingScreen(onClosed = ::finish)
            }
        }
    }

    internal companion object {
        /** The number to write, as `MeasureId.value`. Read by [TodayReadingViewModel]. */
        const val EXTRA_MEASURE_ID = "measure_id"

        /**
         * What the number widget's square starts. ⚠ No data URI: Glance gives each widget's tap a
         * unique one only when the intent has none (checked in the 1.2.0 AAR), and that is what keeps
         * two number widgets from sharing one `PendingIntent` — and one number's dialog.
         */
        fun intent(
            context: Context,
            measureId: MeasureId,
        ): Intent =
            Intent(context, TodayReadingActivity::class.java)
                .putExtra(EXTRA_MEASURE_ID, measureId.value)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@Composable
private fun TodayReadingScreen(
    onClosed: () -> Unit,
    viewModel: TodayReadingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = rememberCurrentLocale()
    LaunchedEffect(viewModel, locale) { viewModel.onEvent(TodayReadingEvent.Started(locale)) }
    LaunchedEffect(viewModel) { viewModel.closed.collect { onClosed() } }
    TodayReadingContent(state = state, locale = locale, onEvent = viewModel::onEvent)
}

/** Nothing while loading — the launcher stays visible for the frame or two it takes. */
@Composable
private fun TodayReadingContent(
    state: TodayReadingUiState,
    locale: Locale,
    onEvent: (TodayReadingEvent) -> Unit,
) {
    if (state !is TodayReadingUiState.Open) return
    ReadingEntryDialog(
        entry = state.entry,
        locale = locale,
        onTextChange = { text -> onEvent(TodayReadingEvent.TextChanged(text)) },
        onSave = { onEvent(TodayReadingEvent.Saved) },
        onClear = { onEvent(TodayReadingEvent.Cleared) },
        onDismiss = { onEvent(TodayReadingEvent.Dismissed) },
    )
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 400)
@Composable
private fun TodayReadingContentPreview() {
    HabitsTheme {
        TodayReadingContent(
            state =
                TodayReadingUiState.Open(
                    ReadingEntry(
                        measureId = MeasureId("weight"),
                        measureName = "Weight",
                        unit = "kg",
                        date = LocalDate.of(2026, 10, 19),
                        text = "",
                        hadValue = false,
                    ),
                ),
            locale = Locale.US,
            onEvent = {},
        )
    }
}
