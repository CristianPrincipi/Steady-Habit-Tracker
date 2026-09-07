package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.domain.model.MappingIssue
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * A [SyncFailure], said as the thing to do about it.
 *
 * ⭐ **In `core/designsystem` rather than in a feature, and that is the whole point.** Two screens
 * show these — the connection wizard (M4.12) and sync status (M4.13) — and they must not end up
 * calling the same `401` different things. One composable and one string per case is what makes
 * that structural rather than a convention someone has to remember.
 *
 * The `when` below is why [SyncFailure] is a closed set: adding a way to fail stops this compiling,
 * so no failure can ship without a sentence.
 *
 * **The warning palette, not the error one.** It is the prototype's own banner treatment
 * (`docs/design-spec.md` §6.2), and right for these: almost every case is something the user can go
 * and fix, and none of them has lost any data.
 */
@Composable
fun NotionFailureBanner(
    failure: SyncFailure,
    modifier: Modifier = Modifier,
) {
    NotionFailureNote(
        text = stringResource(failure.messageRes()),
        modifier = modifier,
    ) {
        if (failure is SyncFailure.MappingBroken) {
            failure.issues.forEach { issue ->
                Text(
                    text = issue.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = HabitsTheme.colors.text.secondary,
                )
            }
        }
    }
}

/**
 * The same banner, said in words that are not a [SyncFailure].
 *
 * ⭐ **Added in M13.12 for the one failure that is not about Notion**: a disconnect that could not
 * write. That is a **local storage** failure, and giving it a `SyncFailure` case would have put it
 * into two exhaustive `when`s and a persisted store that are all about what the *API* said — so the
 * shell is shared and the taxonomy is not.
 *
 * Extracted rather than copied, so the two can never drift into calling the same trouble different
 * colours, which is the reason [NotionFailureBanner] lives in `core/designsystem` at all.
 */
@Composable
fun NotionFailureNote(
    text: String,
    modifier: Modifier = Modifier,
    details: @Composable () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = HabitsTheme.colors.warning.copy(alpha = BANNER_FILL_ALPHA),
                    shape = MaterialTheme.shapes.large,
                ).border(
                    width = Sizing.hairlineBorder,
                    color = HabitsTheme.colors.warning.copy(alpha = BANNER_BORDER_ALPHA),
                    shape = MaterialTheme.shapes.large,
                ).padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.warning,
        )
        details()
    }
}

private fun SyncFailure.messageRes(): Int =
    when (this) {
        SyncFailure.NotConnected -> R.string.notion_failure_not_connected
        SyncFailure.TokenRejected -> R.string.notion_failure_token_rejected
        SyncFailure.MissingCapability -> R.string.notion_failure_missing_capability
        SyncFailure.DatabaseNotShared -> R.string.notion_failure_database_not_shared
        is SyncFailure.MappingBroken -> R.string.notion_failure_mapping_broken
        SyncFailure.Offline -> R.string.notion_failure_offline
        SyncFailure.NotionUnavailable -> R.string.notion_failure_notion_unavailable
        SyncFailure.AppOutOfDate -> R.string.notion_failure_app_out_of_date
    }

/**
 * One mapping problem as a line of text.
 *
 * A deleted property has no name left to show — it is gone from the schema — so those lines are
 * deliberately vaguer than the retyped ones, which can still name the column.
 */
@Composable
private fun MappingIssue.describe(): String =
    when (this) {
        is MappingIssue.DayKeyMissing -> stringResource(R.string.notion_issue_day_key_missing)
        is MappingIssue.DayKeyRetyped -> stringResource(R.string.notion_issue_day_key_retyped, actualType)
        is MappingIssue.HabitMissing -> stringResource(R.string.notion_issue_habit_missing)
        is MappingIssue.HabitRetyped -> stringResource(R.string.notion_issue_habit_retyped, name, actualType)
        MappingIssue.WrongDataSource -> stringResource(R.string.notion_issue_wrong_data_source)
    }

private const val BANNER_FILL_ALPHA = 0.08f
private const val BANNER_BORDER_ALPHA = 0.20f

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun NotionFailureBannerPreview() {
    HabitsTheme {
        Column(
            modifier = Modifier.padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            // The one that matters most: CLAUDE.md §3.3's case, which must never read "not found".
            NotionFailureBanner(failure = SyncFailure.DatabaseNotShared)
            NotionFailureBanner(
                failure =
                    SyncFailure.MappingBroken(
                        listOf(
                            MappingIssue.HabitMissing("ZI%40W"),
                            MappingIssue.HabitRetyped("Ye%3Aq", "✒️ Journal", "number"),
                        ),
                    ),
            )
        }
    }
}
