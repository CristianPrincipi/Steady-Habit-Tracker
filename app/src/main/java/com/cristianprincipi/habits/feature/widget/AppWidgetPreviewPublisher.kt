package com.cristianprincipi.habits.feature.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.cristianprincipi.habits.core.widget.WidgetPreviewPublisher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one implementation of [WidgetPreviewPublisher]: composes each widget's preview and hands it to
 * the system — M15.9.
 *
 * ### ⭐⭐ What this fixes, and it was an absence rather than a bug
 *
 * Neither widget-info file declared `previewImage` or `previewLayout`, so
 * `AppWidgetProviderInfo.previewImage` was 0 and the picker fell back to **the app icon, through the
 * launcher's own preview cache**. That cache was still serving the pre-M14.1 mark months after the
 * icon had been replaced, which is what the user saw. A pushed preview sidesteps it entirely,
 * because there is no longer a fallback to cache.
 *
 * ### ⚠ Why this is not folded into `AppWidgetRefresher`
 *
 * `setWidgetPreviews` is **rate-limited by the platform** — it answers
 * `SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED` rather than failing — and a refresh fires on every
 * check-off. Publishing there would spend the allowance on ticks nobody is looking at and then have
 * none left for the launch that matters. See [WidgetPreviewPublisher] for the full split.
 *
 * ### ⚠⚠ Two things about `setWidgetPreviews` that are not obvious from its signature
 *
 * It builds the **receiver** by reflection (`Class.getConstructors().newInstance()`, read off the
 * Glance 1.2.0 bytecode) and asks *it* for the `GlanceAppWidget` — so it takes the receiver class,
 * not the widget, and R8 must keep that no-arg constructor. AGP's manifest-component rules already
 * do, which is why `proguard-rules.pro` stays at two lines (ADR-141).
 *
 * And it composes the widget **now**, on this thread's dispatcher, through
 * `providePreview` — so the sample state is resolved against live Room on every launch. That is
 * deliberate: reorder your habits and the next launch re-previews with the new first one.
 */
@Singleton
class AppWidgetPreviewPublisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetPreviewPublisher {
        override suspend fun publish() {
            // ⚠ Not defensive tidiness: the call is @RequiresApi under the hood, and Android Lint's
            // NewApi check is part of this project's release gate.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
            val manager = GlanceAppWidgetManager(context)
            // Both, every time. A user who has placed neither still opens the picker, and that is
            // the only place either of these is ever seen.
            manager.setWidgetPreviews(HabitGridWidgetReceiver::class)
            manager.setWidgetPreviews(MeasureWidgetReceiver::class)
        }
    }
