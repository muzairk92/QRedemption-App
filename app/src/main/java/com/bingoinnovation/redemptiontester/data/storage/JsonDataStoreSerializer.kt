package com.bingoinnovation.redemptiontester.data.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.bingoinnovation.redemptiontester.data.security.DataCipher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/** JSON used for everything stored on the device. Unknown keys are ignored for upgrades. */
internal val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Stores a value as JSON in a Jetpack DataStore file, optionally encrypted with [cipher].
 * Unreadable data is reported as [CorruptionException] so the store can reset itself.
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
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Stored data cannot be decrypted.", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Stored data cannot be read.", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plain = json.encodeToString(serializer, t).encodeToByteArray()
        output.write(cipher?.encrypt(plain) ?: plain)
    }
}
