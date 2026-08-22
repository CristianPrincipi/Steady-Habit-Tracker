package com.cristianprincipi.habits.core.datetime

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Puts one [Clock] on the graph, so that nothing below the UI has to ask the system for the time.
 *
 * Everything that needs "now" — the repository stamping a local edit, M3.9's use cases resolving
 * today, M4's sync engine comparing edit times — takes a [Clock] and is testable because of it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DateTimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemDefaultZoneClock
}
