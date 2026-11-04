package com.bingoinnovation.redemptiontester.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts data at rest. Failures are reported as [GeneralSecurityException]. */
interface DataCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(encrypted: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it.
 *
 * Stored format: `[version: 1 byte][IV length: 1 byte][IV][ciphertext + 16-byte tag]`.
 */
class KeystoreDataCipher(private val keyAlias: String) : DataCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.size < HEADER_SIZE || encrypted[0] != FORMAT_VERSION) {
            throw GeneralSecurityException("Unknown encrypted data format.")
        }
        val ivSize = encrypted[1].toInt()
        if (ivSize <= 0 || encrypted.size < HEADER_SIZE + ivSize + TAG_SIZE_BYTES) {
            throw GeneralSecurityException("Encrypted data is truncated.")
        }
        val iv = encrypted.copyOfRange(HEADER_SIZE, HEADER_SIZE + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(encrypted, HEADER_SIZE + ivSize, encrypted.size - HEADER_SIZE - ivSize)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
        const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        const val FORMAT_VERSION: Byte = 1
        const val HEADER_SIZE = 2
    }
}
