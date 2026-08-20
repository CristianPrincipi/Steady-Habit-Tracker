package com.cristianprincipi.habits

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt's dependency graph root.
 *
 * Deliberately empty of logic: WorkManager's sync schedule (M4.10) and anything else that must
 * survive process death is registered by its own initialiser, not by piling work into `onCreate`.
 */
@HiltAndroidApp
class HabitsApplication : Application()
