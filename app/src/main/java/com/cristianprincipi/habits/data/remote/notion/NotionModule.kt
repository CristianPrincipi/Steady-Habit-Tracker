package com.cristianprincipi.habits.data.remote.notion

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/**
 * The Notion client's bindings.
 *
 * **`@Singleton` on the [HttpClient] is not a formality.** A Ktor client owns a connection pool and
 * an engine thread pool; one per call site would mean a fresh TCP handshake and a fresh TLS
 * negotiation for every request, which on a phone radio costs far more than the request itself. It
 * is also what lets the shared [com.cristianprincipi.habits.core.network.RateLimiter] mean anything
 * — a limiter in front of many clients still opens many sockets.
 *
 * The engine is chosen here and **only** here: [notionHttpClient] takes an engine so the tests can
 * hand it a `MockEngine` and still exercise the real configuration.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotionNetworkModule {
    @Provides
    @Singleton
    fun provideNotionHttpClient(): HttpClient = notionHttpClient(OkHttp.create())
}

@Module
@InstallIn(SingletonComponent::class)
interface NotionApiModule {
    @Binds
    @Singleton
    fun bindNotionApi(impl: KtorNotionApi): NotionApi
}
