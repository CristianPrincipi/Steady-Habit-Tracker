package com.cristianprincipi.habits.data.remote.notion.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/**
 * The real Keystore cipher, and the check `CLAUDE.md` §3.6 actually asks for.
 *
 * `AndroidKeyStore` exists only on a device, so none of this can run on the JVM. The store's own
 * logic is covered there instead, over a fake cipher.
 *
 * **[theTokenIsNotReadableFromTheDataDirectory] is the one that matters.** Everything else here
 * tests that the crypto behaves like crypto; that one tests the property the whole step exists for,
 * and it is the only one that would catch somebody quietly replacing the cipher with a
 * pass-through. `Plan.md` M4.4 words it as "verify the token is not readable from a plain
 * data-directory dump" — this is that dump, performed from inside the app.
 *
 * **No real token appears here.** The fixture is an obvious fake (`CLAUDE.md` §3.6: never put a
 * token in a test fixture).
 */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cipher = KeystoreTokenCipher()
    private val token = NotionToken("ntn_NOT_A_REAL_TOKEN_c7f2a91b4e6d8035")

    private lateinit var file: File
    private var scope: CoroutineScope? = null

    @Before
    fun setUp() {
        file = File(context.filesDir, "auth_test_${System.nanoTime()}.preferences_pb")
    }

    @After
    fun tearDown() {
        scope?.cancel()
        file.delete()
        cipher.invalidate()
    }

    private fun dataStore(): DataStore<Preferences> {
        scope?.cancel()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        return PreferenceDataStoreFactory.create(scope = newScope) { file }
    }

    // ─── The acceptance test ───────────────────────────────────────────────────────────────────

    @Test
    fun theTokenIsNotReadableFromTheDataDirectory() =
        runBlocking {
            val store = DataStoreNotionTokenStore(dataStore(), cipher)

            store.save(token)

            // Read the file exactly as `adb shell run-as … cat` would, and hunt for the secret in
            // every form it could plausibly have reached disk in.
            val onDisk = file.readBytes()
            val asText = String(onDisk, Charsets.ISO_8859_1)
            assertTrue("The file was never written", onDisk.isNotEmpty())

            assertFalse(
                "THE TOKEN IS SITTING IN THE DATA DIRECTORY IN PLAIN TEXT.",
                asText.contains(token.value),
            )
            assertFalse(
                "The token is in the file as UTF-8 bytes.",
                onDisk.containsSubsequence(token.value.toByteArray(Charsets.UTF_8)),
            )
            // **The check the raw-byte search alone would miss**, found by probing on 2026-08-22:
            // the store base64-encodes before writing, so a cipher that quietly became a
            // pass-through would put the token on disk as BASE64 — invisible to a search for the
            // literal string, and trivially readable by anyone who ran the file through a decoder.
            // Without this line the probe passed. See ADR-042.
            assertFalse(
                "THE TOKEN IS ON DISK, BASE64-ENCODED. That is an encoding, not encryption.",
                asText.contains(Base64.getEncoder().encodeToString(token.value.toByteArray(Charsets.UTF_8))),
            )
        }

    @Test
    fun theSavedTokenStillReadsBackThroughTheStore() =
        runBlocking {
            // The other half of the test above: unreadable on disk, readable through the API.
            // Encryption that also broke retrieval would pass the check above and be useless.
            val store = DataStoreNotionTokenStore(dataStore(), cipher)

            store.save(token)

            assertEquals(token, store.read())
            assertTrue(store.isConnected.first())
        }

    @Test
    fun aDisconnectLeavesNothingReadableBehind() =
        runBlocking {
            val store = DataStoreNotionTokenStore(dataStore(), cipher)
            store.save(token)

            store.clear()

            assertNull(store.read())
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(token.value))
        }

    // ─── The cipher itself ─────────────────────────────────────────────────────────────────────

    @Test
    fun itRoundTrips() {
        assertEquals(token.value, cipher.decrypt(cipher.encrypt(token.value)))
    }

    @Test
    fun theCiphertextIsNotThePlaintext() {
        val blob = cipher.encrypt(token.value)

        assertFalse(String(blob, Charsets.ISO_8859_1).contains(token.value))
    }

    @Test
    fun encryptingTheSameValueTwiceGivesDifferentBytes() {
        // The IV is random per encryption, so identical plaintexts must not produce identical
        // ciphertexts. If they did, the Keystore's randomised-encryption requirement would have
        // been switched off — and reusing an IV under one GCM key leaks the keystream.
        val first = cipher.encrypt(token.value)
        val second = cipher.encrypt(token.value)

        assertNotEquals(first.toList(), second.toList())
        assertEquals(token.value, cipher.decrypt(first))
        assertEquals(token.value, cipher.decrypt(second))
    }

    @Test
    fun aTamperedCiphertextDoesNotDecrypt() {
        // What the GCM tag is for. A ciphertext edited in place must fail rather than yield
        // plausible-looking garbage.
        val blob = cipher.encrypt(token.value)
        blob[blob.lastIndex] = (blob[blob.lastIndex] + 1).toByte()

        assertNull(cipher.decrypt(blob))
    }

    @Test
    fun aTruncatedBlobDoesNotCrash() {
        assertNull(cipher.decrypt(ByteArray(0)))
        assertNull(cipher.decrypt(ByteArray(5)))
        assertNull(cipher.decrypt(ByteArray(12)))
    }

    @Test
    fun aCiphertextIsUnreadableOnceTheKeyIsDestroyed() =
        runTest {
            // The disconnect guarantee, and also the device-transfer case: a file that survives
            // without its key opens nothing.
            val blob = cipher.encrypt(token.value)
            assertEquals(token.value, cipher.decrypt(blob))

            cipher.invalidate()

            assertNull("A destroyed key must not still open old ciphertext", cipher.decrypt(blob))
        }
}

/** Whether [this] contains [needle] as a contiguous run of bytes. */
private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    return (0..size - needle.size).any { start ->
        needle.indices.all { this[start + it] == needle[it] }
    }
}
