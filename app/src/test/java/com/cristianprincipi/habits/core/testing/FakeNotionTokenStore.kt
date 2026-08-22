package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.remote.notion.auth.NotionToken
import com.cristianprincipi.habits.data.remote.notion.auth.NotionTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A [NotionTokenStore] held in memory, so a test of the client is a test of the client.
 *
 * The real store needs the Android Keystore and therefore a device (ADR-042); nothing about the
 * Notion client depends on where its token came from, only on whether there is one.
 *
 * **The value below is not a token and never will be.** `CLAUDE.md` §3.6 forbids a real one reaching
 * a test fixture; this one is shaped like the `ntn_` prefix `docs/notion-api.md` §3 records and is
 * otherwise deliberately nonsense, so that a test asserting "the token never leaks" has something
 * unmistakable to search for.
 */
class FakeNotionTokenStore(
    token: NotionToken? = NOT_A_REAL_TOKEN,
) : NotionTokenStore {
    private val stored = MutableStateFlow(token)

    override val isConnected: Flow<Boolean> = stored.map { it != null }

    override suspend fun read(): NotionToken? = stored.value

    override suspend fun save(token: NotionToken) {
        stored.value = token
    }

    override suspend fun clear() {
        stored.value = null
    }

    companion object {
        val NOT_A_REAL_TOKEN = NotionToken("ntn_this-is-not-a-real-token")
    }
}
