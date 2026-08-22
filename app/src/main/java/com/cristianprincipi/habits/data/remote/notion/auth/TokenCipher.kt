package com.cristianprincipi.habits.data.remote.notion.auth

/**
 * Encrypts and decrypts one small secret.
 *
 * An interface so the store above it can be unit tested on the JVM: the real implementation needs
 * the `AndroidKeyStore` provider, which exists only on a device (the same constraint ADR-017 put on
 * Room). The real one is exercised by `KeystoreTokenCipherTest` under `androidTest`.
 */
interface TokenCipher {
    /** @return an opaque blob to persist. Callers must not assume any structure in it. */
    fun encrypt(plaintext: String): ByteArray

    /**
     * @return the plaintext, or `null` if [blob] cannot be decrypted.
     *
     * **Null is an expected outcome, not an error to throw on.** The key lives in the Keystore and
     * the ciphertext lives in app storage, so the two can be separated: a device-to-device transfer
     * or a restore-from-backup can carry the file without the key. `CLAUDE.md` §1's answer is the
     * right one — the app keeps working from Room and asks the user to reconnect. A crash on launch
     * would not.
     */
    fun decrypt(blob: ByteArray): String?

    /**
     * Destroys the key, making every existing ciphertext permanently unreadable.
     *
     * Part of the **disconnect** action `CLAUDE.md` §3.6 requires. On the interface rather than only
     * on the Keystore implementation so that [NotionTokenStore.clear] does not have to downcast to
     * reach it — and so a fake can assert it was called, which is the half of "wipe the token" that
     * a test of the storage alone would miss.
     */
    fun invalidate()
}
