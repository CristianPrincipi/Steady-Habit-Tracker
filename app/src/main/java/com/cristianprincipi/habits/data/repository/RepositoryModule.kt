package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.domain.repository.HabitRepository
import com.cristianprincipi.habits.domain.repository.SettingsRepository
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
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
 */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    fun bindSyncStatusRepository(impl: SyncStatusRepositoryImpl): SyncStatusRepository
}
