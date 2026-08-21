package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Nothing to show, said calmly.
 *
 * **Undesigned.** The prototype is a read-only visualiser with hardcoded habits, so it has no empty,
 * loading or error state anywhere (ADR-008). This is built from the design system's own vocabulary —
 * a large emoji, the title at the screen-title size, a muted line under it — rather than invented
 * from scratch, and it needs the user's eye before it is trusted (`Plan.md` M2.8).
 *
 * The action is optional because most of this app's empty states have nothing to offer: habits come
 * from Notion and the app cannot create one (ADR-010), so "no habits yet" is a sentence, not a
 * button.
 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.secondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                // Material routes a Button to its own shape tier, which is not the design's 14dp
                // for buttons — ADR-022. Stock components get their shape passed explicitly.
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = HabitsTheme.colors.text.primary,
                    ),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun EmptyStatePreview() {
    HabitsTheme {
        EmptyState(
            emoji = "🌱",
            title = "No habits yet",
            body = "Habits live in your Notion database. Add one there and it will appear here after the next sync.",
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400)
@Composable
private fun EmptyStateWithActionPreview() {
    HabitsTheme {
        EmptyState(
            emoji = "🔌",
            title = "Not connected",
            body = "Connect your Notion workspace to see your habits.",
            actionLabel = "Connect to Notion",
            onAction = {},
        )
    }
}
