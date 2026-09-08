package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one implementation of [WidgetRefresher]: tells every placed habit widget to look again.
 *
 * ⭐ **`updateAll` here, where the configuration activity deliberately uses `update(glanceId)`.**
 * That is not an inconsistency: configuring one widget concerns one widget, while a check-off or a
 * pull can change what *any* of them draws. ⚠ And `updateAll` costs nothing for the majority of
 * users who have placed none — `getGlanceIds` returns an empty list and no session is started.
 *
 * ⚠ **It does not guard itself.** `refreshQuietly` is where a failure is contained, so that the
 * guarantee belongs to every implementation rather than to this one — see [WidgetRefresher].
 */
@Singleton
class HabitWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refresh() {
            HabitGridWidget().updateAll(context)
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface WidgetModule {
    @Binds
    fun refresher(impl: HabitWidgetRefresher): WidgetRefresher
}
