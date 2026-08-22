package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.domain.repository.HabitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the repository interfaces the domain declares to the Room-backed implementations.
 *
 * The point of the indirection is `CLAUDE.md` §5's layering: `domain/` owns the interface and knows
 * nothing about Room, so a ViewModel injecting [HabitRepository] cannot reach an entity even by
 * accident.
 *
 * **`SettingsRepository` is not bound yet.** Its implementation needs the DataStore built in M3.7,
 * and there is nothing honest to bind to before that exists.
 */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository
}
