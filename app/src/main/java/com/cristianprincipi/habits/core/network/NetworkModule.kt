package com.cristianprincipi.habits.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The shared network policy objects.
 *
 * **`@Singleton` on the rate limiter is the point of it.** A limiter constructed per call site
 * spaces nothing — each copy keeps its own "next allowed" and they all fire at once. One instance
 * for the process is what makes `CLAUDE.md` §3.5's "never call the API in a bare loop" enforceable
 * rather than a rule people have to remember.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter = RateLimiter()

    /** Stateless, so the scope is a formality — but it is shared, so there is no reason not to. */
    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()
}

@Module
@InstallIn(SingletonComponent::class)
interface ConnectivityModule {
    @Binds
    @Singleton
    fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver
}
