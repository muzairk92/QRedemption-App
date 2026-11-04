package com.bingoinnovation.redemptiontester.data.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.bingoinnovation.redemptiontester.data.security.DataCipher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException

/** JSON used for everything stored on the device. Unknown keys are ignored for upgrades. */
internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Stores a value as JSON in a Jetpack DataStore file, optionally encrypted with [cipher].
 *
 * - Data that can never be read again (bad JSON, wrong key, damaged bytes) is reported as
 *   [CorruptionException], so the store resets itself.
 * - A Keystore that is only unavailable right now ([KeyStoreException], or the
 *   [ProviderException] Android throws when its keystore service fails) is reported as
 *   [IOException], so nothing is wiped and the next read can succeed.
 * - Any failure while saving is an [IOException], the error repositories document.
 */
internal class JsonDataStoreSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val cipher: DataCipher? = null,
    private val json: Json = StorageJson,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val stored = input.readBytes()
        if (stored.isEmpty()) return defaultValue
        return try {
            val plain = cipher?.decrypt(stored) ?: stored
            json.decodeFromString(serializer, plain.decodeToString())
        } catch (e: KeyStoreException) {
            throw IOException("The key store is not available.", e)
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Stored data cannot be decrypted.", e)
        } catch (e: ProviderException) {
            throw IOException("The key store is not available.", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Stored data cannot be read.", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plain = json.encodeToString(serializer, t).encodeToByteArray()
        val stored = try {
            cipher?.encrypt(plain) ?: plain
        } catch (e: GeneralSecurityException) {
            throw IOException("Data cannot be encrypted.", e)
        } catch (e: ProviderException) {
            throw IOException("The key store is not available.", e)
        }
        output.write(stored)
    }
}
