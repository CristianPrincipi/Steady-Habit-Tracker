package com.cristianprincipi.habits.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.HabitsCard
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.component.readingWidth
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Everything the About screen draws — M11.8, and the last live step in M11.
 *
 * ### ⚠⚠ This screen exists to say true things
 *
 * `CLAUDE.md` §9.12 records that the app's old privacy line — *"all data stays on your device"* — is
 * now **false**: habit data is transmitted to the user's Notion workspace at their instruction. This
 * is where that is corrected in the user's own words, and **M14.6's Play Data Safety declaration
 * inherits whatever is written here**, so a vague sentence on this screen becomes a wrong
 * declaration on that form.
 *
 * The order of the data section is deliberate: where the habits actually live, then what a tap does,
 * then the single host, then the token, then what disconnecting destroys. It ends on the sentence a
 * worried user is looking for — nothing in Notion is ever deleted by this app (ADR-036: v1 has no
 * delete at all).
 *
 * ### ⚠ Undesigned, in full
 *
 * The prototype has no About view (`docs/design-spec.md`), so every word and every arrangement here
 * is ours. It is built from M2's card, eyebrow and type scale so it reads as the same app;
 * `Plan.md` M2.8 applies, and the user judges it on a device.
 *
 * ⭐ **There is no `AboutUiState` and no ViewModel.** Nothing on this screen can change: a version
 * fixed at build time, fixed prose and three fixed licence notices. A state class here would be one
 * that can never be in a second state, and a ViewModel would exist to observe nothing.
 */
@Composable
fun AboutContent(
    versionName: String,
    versionCode: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        // No `windowInsetsPadding`, and the title is inside the padded column rather than beside it:
        // this is a destination reached from Settings, like `SyncStatusContent`, not one of the five
        // tabs — which is exactly the distinction M11.1 got wrong and `HabitsShellInsetsTest` caught.
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // ⭐ M12.8, and it is placed **after** the scroll on purpose: the gesture stays the
                // width of the window while the prose is capped, so a landscape reader can flick
                // anywhere rather than only inside the centred column.
                .readingWidth()
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        AboutHeader(versionName = versionName, versionCode = versionCode)
        DataSection()
        LicenceSection()
    }
}

/**
 * The title, with the version directly under it.
 *
 * The version is the smallest true fact on the screen and the one people come here for first, so it
 * sits at the top rather than at the bottom where an "app info" footer would put it.
 */
@Composable
private fun AboutHeader(
    versionName: String,
    versionCode: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.headerBottom)) {
        Text(
            text = stringResource(R.string.screen_title_about),
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )
        Text(
            text = stringResource(R.string.about_version, versionName, versionCode),
            style = MaterialTheme.typography.bodySmall,
            color = HabitsTheme.colors.text.secondary,
        )
    }
}

/** What leaves the device, in five sentences that each have to survive being checked. */
@Composable
private fun DataSection() {
    HabitsCard(modifier = Modifier.fillMaxWidth()) {
        SectionEyebrow(text = stringResource(R.string.about_data_eyebrow))
        Paragraph(R.string.about_data_home)
        Paragraph(R.string.about_data_offline)
        Paragraph(R.string.about_data_one_host)
        Paragraph(R.string.about_data_token)
        Paragraph(R.string.about_data_disconnect)
    }
}

/**
 * The three licences the APK already ships.
 *
 * Naming the holder and the licence is what the OFL and the ISC require of a redistributor; the full
 * texts are in `assets/licences/` and have been since M2.5. A licences *viewer* is post-v1 —
 * `docs/attributions.md` says so, and shipping the texts already satisfies both licences.
 */
@Composable
private fun LicenceSection() {
    HabitsCard(modifier = Modifier.fillMaxWidth()) {
        SectionEyebrow(text = stringResource(R.string.about_licences_eyebrow))
        Paragraph(R.string.about_licences_intro)
        Licence(R.string.about_licence_dm_sans, R.string.about_licence_dm_sans_terms)
        Licence(R.string.about_licence_dm_mono, R.string.about_licence_dm_mono_terms)
        Licence(R.string.about_licence_lucide, R.string.about_licence_lucide_terms)
    }
}

/** One line of prose, at the emphasis every other explanatory paragraph in the app uses. */
@Composable
private fun Paragraph(textId: Int) {
    Text(
        text = stringResource(textId),
        style = MaterialTheme.typography.bodySmall,
        color = HabitsTheme.colors.text.secondary,
    )
}

/**
 * A licence: what it covers, then the terms it covers it under.
 *
 * The two lines sit at `yearDotGap` rather than the card's own gap, for the reason the status card
 * stacks its two lines that way — they are one statement, and 12dp between them would read as two.
 */
@Composable
private fun Licence(
    nameId: Int,
    termsId: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.yearDotGap)) {
        Text(
            text = stringResource(nameId),
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.primary,
        )
        Text(
            text = stringResource(termsId),
            style = MaterialTheme.typography.bodySmall,
            color = HabitsTheme.colors.text.secondary,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun AboutPreview() {
    HabitsTheme {
        AboutContent(versionName = "1.0.0", versionCode = 1)
    }
}
