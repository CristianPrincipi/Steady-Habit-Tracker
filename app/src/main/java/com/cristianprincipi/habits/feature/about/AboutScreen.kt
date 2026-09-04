package com.cristianprincipi.habits.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.BuildConfig

/**
 * About — version, what leaves the device, and the licences the APK ships (M11.8).
 *
 * Stateful half only, and it holds no state: see [AboutContent] for everything drawn and for why
 * there is no `AboutUiState` and no ViewModel behind it.
 *
 * ⭐ **The version comes from [BuildConfig], which M11.8 had to switch on.** `buildFeatures` carried
 * `compose = true` and nothing else, and nothing in the codebase referenced `BuildConfig` at all.
 * The alternative was `PackageManager.getPackageInfo`, which would have put a `Context` on the one
 * screen in the app that otherwise needs nothing — and `CLAUDE.md` §6 keeps `Context` out of the
 * layers that would have to carry it. Reading it here rather than inside [AboutContent] is what lets
 * the content be previewed and tested at any version.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        modifier = modifier,
    )
}
