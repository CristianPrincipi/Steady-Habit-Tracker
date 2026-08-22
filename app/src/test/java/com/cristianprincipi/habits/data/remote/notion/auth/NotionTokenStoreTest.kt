package com.cristianprincipi.habits.data.remote.notion.auth

import com.cristianprincipi.habits.core.testing.FailingDataStore
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The token store's decisions, over a fake cipher.
 *
 * The real cipher needs a device — `AndroidKeyStore` exists nowhere else — so it is proved in
 * `KeystoreTokenCipherTest`, together with the check that actually matters for `CLAUDE.md` §3.6:
 * that the token cannot be read out of the file on disk.
 *
 * What is worth testing here is everything around the crypto, and most of it is about failure: a
 * blob that will not decrypt, a file that will not read, and a disconnect that has to destroy both
 * halves.
 */
class NotionTokenStoreTest {
    private val token = NotionToken("ntn_fakeTokenForTestsOnly")

    /** Reversible, obviously not secure, and obviously not the plaintext. */
    private class ReversingCipher(
        var failDecryption: Boolean = false,
    ) : TokenCipher {
        var invalidated = false
            private set

        override fun encrypt(plaintext: String): ByteArray = plaintext.reversed().toByteArray()

        override fun decrypt(blob: ByteArray): String? = if (failDecryption) null else String(blob).reversed()

        override fun invalidate() {
            invalidated = true
        }
    }

    private fun store(
        cipher: TokenCipher = ReversingCipher(),
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
            InMemoryDataStore(),
    ) = DataStoreNotionTokenStore(dataStore, cipher)

    @Test
    fun aFreshInstallHasNoToken() =
        runTest {
            val store = store()

            assertNull(store.read())
            assertFalse(store.isConnected.first())
        }

    @Test
    fun aSavedTokenComesBack() =
        runTest {
            val store = store()

            store.save(token)

            assertEquals(token, store.read())
            assertTrue(store.isConnected.first())
        }

    @Test
    fun savingTwiceKeepsTheLatest() =
        runTest {
            val store = store()
            store.save(token)

            store.save(NotionToken("ntn_theSecondOne"))

            assertEquals(NotionToken("ntn_theSecondOne"), store.read())
        }

    @Test
    fun disconnectRemovesTheTokenAndDestroysTheKey() =
        runTest {
            // Both halves. Removing only the ciphertext leaves a key that can still open a copy of
            // the file recovered from a backup; destroying only the key leaves something that still
            // looks like a stored token to anything reading the file directly.
            val cipher = ReversingCipher()
            val store = store(cipher)
            store.save(token)

            store.clear()

            assertNull(store.read())
            assertFalse(store.isConnected.first())
            assertTrue("The Keystore key must be destroyed too", cipher.invalidated)
        }

    @Test
    fun aTokenThatCanNoLongerBeDecryptedReadsAsAbsent() =
        runTest {
            // The device-transfer case: the file came across, the Keystore key did not. CLAUDE.md §1
            // says the app keeps working from Room and asks the user to reconnect — a crash on
            // launch would not.
            val cipher = ReversingCipher()
            val store = store(cipher)
            store.save(token)

            cipher.failDecryption = true

            assertNull(store.read())
            assertFalse(store.isConnected.first())
        }

    @Test
    fun isConnectedMeansDecryptableNotMerelyPresent() =
        runTest {
            // The distinction the test above turns on. Reporting "connected" for a blob that cannot
            // be opened would send the user to a screen where every sync fails silently.
            val cipher = ReversingCipher()
            val store = store(cipher)
            store.save(token)
            assertTrue(store.isConnected.first())

            cipher.failDecryption = true

            assertFalse(store.isConnected.first())
        }

    @Test
    fun anUnreadableFileMeansNotConnectedRatherThanACrash() =
        runTest {
            val store = store(dataStore = FailingDataStore(IOException("corrupt")))

            assertNull(store.read())
            assertFalse(store.isConnected.first())
        }

    @Test
    fun aFailureThatIsNotIoStillPropagates() =
        runTest {
            // Narrow on purpose: a real bug must not be laundered into "not connected", which would
            // send the user round the connection flow forever.
            val store = store(dataStore = FailingDataStore(IllegalStateException("a real bug")))

            val thrown = runCatching { store.read() }.exceptionOrNull()

            assertEquals("a real bug", thrown?.message)
        }

    @Test
    fun whatIsWrittenIsNotThePlaintext() =
        runTest {
            // Weak on its own — the fake cipher only reverses the string — but it does prove the
            // store encrypts before writing rather than passing the value through. The real
            // property is proved on device in KeystoreTokenCipherTest.
            val dataStore = InMemoryDataStore()
            store(dataStore = dataStore).save(token)

            val written =
                dataStore.data
                    .first()
                    .asMap()
                    .values
                    .single()
                    .toString()

            assertFalse("The raw token reached the file", written.contains(token.value))
        }

    @Test
    fun theTokenNeverPrintsItself() {
        // CLAUDE.md §3.6: never log the token. The ways a String leaks are all implicit — string
        // interpolation, a data class's generated toString, an exception message.
        assertFalse("$token".contains(token.value))
        assertEquals("NotionToken(***)", token.toString())
    }

    @Test
    fun aPastedTokenIsSanityCheckedButNotValidated() {
        assertTrue(NotionToken("ntn_something").looksLikeAToken())
        assertFalse("A page URL is the usual mis-paste", NotionToken("https://notion.so/x").looksLikeAToken())
        assertFalse(NotionToken("").looksLikeAToken())
        assertFalse("The prefix alone is not a token", NotionToken("ntn_").looksLikeAToken())
    }
}
