package com.cristianprincipi.habits.data.remote.notion.auth

import kotlinx.coroutines.flow.Flow

/**
 * Where the user's Notion secret lives.
 *
 * The whole of `CLAUDE.md` §3.6 lands on this one interface: the token grants full read/write access
 * to every Notion page shared with the connection, so it is treated as a password — stored
 * encrypted under the Android Keystore, never logged, never exported, and removable on demand.
 *
 * **Nothing above `data/remote/notion` should ever hold a [NotionToken].** The client attaches it to
 * requests; the UI knows only [isConnected]. A screen that receives the token in its state has put
 * it into a `UiState` that something will eventually print.
 */
interface NotionTokenStore {
    /**
     * Whether a token is stored — **not whether it works**.
     *
     * A revoked token is still stored. `401 unauthorized` is what tells us otherwise, and that is
     * the sync engine's business (M4.11), not this interface's. Drives which screen the app shows
     * on launch, so it is a `Flow`.
     */
    val isConnected: Flow<Boolean>

    /** @return the stored token, or `null` if there is none or it can no longer be decrypted. */
    suspend fun read(): NotionToken?

    suspend fun save(token: NotionToken)

    /**
     * Wipes the token. The **disconnect** action `CLAUDE.md` §3.6 requires.
     *
     * Removes the ciphertext *and* the Keystore key, so nothing readable survives even if the data
     * file is recovered afterwards. Wiping the synced habit data is a separate, optional choice the
     * user makes on the same screen (M4.13) — losing history is not implied by disconnecting, and
     * for a habit whose Notion property was since deleted the local copy may be the only one left.
     */
    suspend fun clear()
}
