package com.cristianprincipi.habits.data.remote.notion.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM, with the key held in the Android Keystore.
 *
 * **Why this and not a library.** `CLAUDE.md` §3.6 says to confirm the currently recommended
 * approach rather than reach for a remembered one, and M4.4 did: `androidx.security:security-crypto`
 * — `EncryptedSharedPreferences`, `EncryptedFile` — is **deprecated**, with the release notes stating
 * plainly that "there won't be any subsequent releases of this library". Android's own cryptography
 * guidance recommends "AES in either CBC or GCM mode with 256-bit keys (such as `AES/GCM/NoPadding`)".
 * That is the platform API, so this costs no dependency and cannot be deprecated out from under us
 * the same way. See ADR-042.
 *
 * **The key never leaves the Keystore.** [SecretKey] here is a handle; the bytes live in hardware-
 * backed storage where the OS will not hand them out. Copying the app's data directory therefore
 * copies ciphertext and nothing that can open it.
 *
 * **GCM, and the IV is never ours to choose.** `setRandomizedEncryptionRequired` defaults to true
 * and is deliberately left there: the Keystore then refuses an [Cipher.ENCRYPT_MODE] init that
 * supplies an IV, which makes the classic GCM catastrophe — reusing an IV under one key, which
 * leaks the keystream and forges tags — unreachable rather than merely avoided. The IV it generates
 * is prepended to the ciphertext, because it must be stored and is not secret.
 */
@Singleton
class KeystoreTokenCipher
    @Inject
    constructor() : TokenCipher {
        override fun encrypt(plaintext: String): ByteArray {
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.ENCRYPT_MODE, secretKey())
                }
            // iv || ciphertext+tag. The IV is public by design; only the key is secret.
            return cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        }

        /**
         * Swallowing the exception here is the point, not an oversight.
         *
         * Every `GeneralSecurityException` this can throw means the same thing to the caller —
         * there is no usable token — and none is worth a crash. Propagating it would put a
         * cryptographic failure on a stack trace that `CLAUDE.md` §3.6 says must never carry
         * anything token-shaped, and logging it would defeat the point of not propagating it: an
         * exception message can echo what the cipher was handed.
         */
        @Suppress("SwallowedException")
        override fun decrypt(blob: ByteArray): String? {
            if (blob.size <= IV_LENGTH) return null
            return try {
                val cipher =
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(
                            Cipher.DECRYPT_MODE,
                            secretKey(),
                            GCMParameterSpec(TAG_LENGTH_BITS, blob, 0, IV_LENGTH),
                        )
                    }
                String(cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH), Charsets.UTF_8)
            } catch (failure: GeneralSecurityException) {
                // Wrong key, tampered ciphertext, or a key that no longer exists. All mean the same
                // thing to the caller — there is no usable token — and none of them is worth a
                // crash. Deliberately NOT logged: a failure message can echo what it was given.
                null
            }
        }

        override fun invalidate() {
            keyStore().deleteEntry(KEY_ALIAS)
        }

        private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        private fun secretKey(): SecretKey {
            val existing = (keyStore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            return existing ?: generateKey()
        }

        private fun generateKey(): SecretKey =
            KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(KEY_SIZE_BITS)
                            // No setUserAuthenticationRequired: sync runs from WorkManager while the
                            // device is locked (CLAUDE.md §3.1), and a key gated on unlock would
                            // make every background sync fail. The token is protected by the app
                            // sandbox and the Keystore, not by a prompt.
                            .build(),
                    )
                }.generateKey()

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val TRANSFORMATION = "AES/GCM/NoPadding"

            /**
             * Changing this orphans the old key and every ciphertext under it.
             *
             * Which is a legitimate way to invalidate a stored token — but it is silent, so do it
             * on purpose or not at all.
             */
            const val KEY_ALIAS = "notion_token_v1"

            const val KEY_SIZE_BITS = 256

            /** GCM's standard 96-bit nonce. Any other length costs performance and gains nothing. */
            const val IV_LENGTH = 12

            /** The full 128-bit tag. A shortened tag weakens forgery resistance for 8 saved bytes. */
            const val TAG_LENGTH_BITS = 128
        }
    }
