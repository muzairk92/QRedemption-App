package com.bingoinnovation.redemptiontester.data.storage

import androidx.datastore.core.CorruptionException
import com.bingoinnovation.redemptiontester.data.security.DataCipher
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionAttempt
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict
import com.bingoinnovation.redemptiontester.testing.TestData
import com.bingoinnovation.redemptiontester.testing.XorDataCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException

class JsonDataStoreSerializerTest {

    private val vaultSerializer = JsonDataStoreSerializer(Vault.serializer(), Vault(), XorDataCipher())

    private val vault = Vault(
        caller = TestData.credentials,
        attempts = listOf(
            RedemptionAttempt(
                id = "a1",
                scenario = TestScenario.FIRST_REDEMPTION,
                requestId = TestData.REQUEST_ID,
                ticketQr = TestData.TICKET_TEXT,
                tabletId = TestData.TABLET_ID,
                callerId = TestData.CALLER_ID,
                serverBaseUrl = "https://staging.bingoinnovation.com",
                startedAtMillis = 1_000,
                finishedAtMillis = 2_000,
                outcome = TestData.success(),
                verdict = Verdict.AS_EXPECTED,
            ),
            RedemptionAttempt(
                id = "a2",
                scenario = TestScenario.NEW_REQUEST_SAME_TICKET,
                requestId = "11111111-1111-4111-8111-111111111111",
                ticketQr = TestData.TICKET_TEXT,
                tabletId = TestData.TABLET_ID,
                callerId = TestData.CALLER_ID,
                serverBaseUrl = "https://staging.bingoinnovation.com",
                startedAtMillis = 3_000,
                finishedAtMillis = 4_000,
                outcome = TestData.rejected("already_redeemed", 409),
                verdict = Verdict.AS_EXPECTED,
            ),
        ),
    )

    @Test
    fun vault_roundTripsEveryOutcomeType() = runTest {
        val bytes = ByteArrayOutputStream().also { vaultSerializer.writeTo(vault, it) }.toByteArray()

        val restored = vaultSerializer.readFrom(ByteArrayInputStream(bytes))

        assertThat(restored).isEqualTo(vault)
    }

    @Test
    fun vault_isNotStoredAsPlainText() = runTest {
        val bytes = ByteArrayOutputStream().also { vaultSerializer.writeTo(vault, it) }.toByteArray()
        val stored = bytes.decodeToString()

        assertThat(stored).doesNotContain(TestData.API_KEY)
        assertThat(stored).doesNotContain(TestData.TICKET_TEXT)
    }

    @Test
    fun emptyFile_readsAsDefault() = runTest {
        assertThat(vaultSerializer.readFrom(ByteArrayInputStream(ByteArray(0)))).isEqualTo(Vault())
    }

    @Test
    fun undecryptableOrUnreadableData_isReportedAsCorruption() {
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking { vaultSerializer.readFrom(ByteArrayInputStream("plain".toByteArray())) }
        }
        val settingsSerializer = JsonDataStoreSerializer(AppSettings.serializer(), AppSettings())
        assertThrows(CorruptionException::class.java) {
            kotlinx.coroutines.runBlocking { settingsSerializer.readFrom(ByteArrayInputStream("{not json".toByteArray())) }
        }
    }

    @Test
    fun encryptionFailureWhileSaving_isAStorageError() {
        listOf(GeneralSecurityException("Key invalidated"), ProviderException("Keystore operation failed"))
            .forEach { failure ->
                val serializer = JsonDataStoreSerializer(Vault.serializer(), Vault(), FailingCipher(failure))
                assertThrows(IOException::class.java) {
                    kotlinx.coroutines.runBlocking { serializer.writeTo(vault, ByteArrayOutputStream()) }
                }
            }
    }

    @Test
    fun keyStoreUnavailableWhileReading_isAStorageError_notCorruption() {
        // Corruption would wipe the vault, losing request IDs that must be retried.
        listOf(KeyStoreException("Keystore not ready"), ProviderException("Keystore operation failed"))
            .forEach { failure ->
                val serializer = JsonDataStoreSerializer(Vault.serializer(), Vault(), FailingCipher(failure))
                val thrown = assertThrows(IOException::class.java) {
                    kotlinx.coroutines.runBlocking { serializer.readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
                }
                assertThat(thrown).isNotInstanceOf(CorruptionException::class.java)
            }
    }

    @Test
    fun settings_ignoreUnknownFieldsFromNewerVersions() = runTest {
        val settingsSerializer = JsonDataStoreSerializer(AppSettings.serializer(), AppSettings())
        val stored = """{"server":{"preset":"CUSTOM","customBaseUrl":"https://p.example"},"tabletId":"t1","future":1}"""

        val restored = settingsSerializer.readFrom(ByteArrayInputStream(stored.toByteArray()))

        assertThat(restored.tabletId).isEqualTo("t1")
        assertThat(restored.server.baseUrl).isEqualTo("https://p.example")
    }

    /** Fails every operation with [failure], as a broken or busy Android Keystore can. */
    private class FailingCipher(private val failure: Exception) : DataCipher {
        override fun encrypt(plain: ByteArray): ByteArray = throw failure

        override fun decrypt(encrypted: ByteArray): ByteArray = throw failure
    }
}
