package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.cristianprincipi.habits.core.widget.WidgetPreviewPublisher
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one implementation of [WidgetRefresher]: tells **every kind** of placed widget to look again.
 *
 * ### ⚠⚠ It was `HabitWidgetRefresher` and it woke habit widgets only — M15.6d's first finding
 *
 * M15.6c's plan said the measure widget would inherit this "for free". Half of that was true: the
 * `WidgetRefresher` **port** costs nothing to reuse, and `SyncOutcome.changedLocalData` genuinely
 * already covers a measure pull. But the body of this class named one widget class, so a measure
 * widget placed on a home screen would have been woken by **nothing at all** — and no gate here
 * would have caught it, because no test in this project names a widget class. Hence the rename:
 * a class called *habit* refresher is one a future session widens by accident or not at all.
 *
 * ⭐ **`updateAll` here, where the configuration activities use `update(glanceId)`.** Not an
 * inconsistency: configuring one widget concerns one widget, while a check-off or a pull can change
 * what *any* of them draws. ⚠ And it costs nothing for the majority of users who have placed none —
 * `getGlanceIds` returns an empty list and no Glance session is started, once per widget class.
 *
 * ⚠ **It does not guard itself.** `refreshQuietly` is where a failure is contained, so the guarantee
 * belongs to every implementation rather than to this one — see [WidgetRefresher].
 */
@Singleton
class AppWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refresh() {
            HabitGridWidget().updateAll(context)
            MeasureWidget().updateAll(context)
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface WidgetModule {
    @Binds
    fun refresher(impl: AppWidgetRefresher): WidgetRefresher

    @Binds
    fun previewPublisher(impl: AppWidgetPreviewPublisher): WidgetPreviewPublisher
}
