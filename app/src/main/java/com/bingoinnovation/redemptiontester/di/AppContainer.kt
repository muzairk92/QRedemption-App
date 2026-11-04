package com.bingoinnovation.redemptiontester.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.bingoinnovation.redemptiontester.BuildConfig
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.data.network.PortalClient
import com.bingoinnovation.redemptiontester.data.network.PortalHttpClientFactory
import com.bingoinnovation.redemptiontester.data.security.KeystoreDataCipher
import com.bingoinnovation.redemptiontester.data.storage.AppSettings
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.JsonDataStoreSerializer
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.data.storage.Vault
import com.bingoinnovation.redemptiontester.domain.redemption.RandomRequestIdGenerator
import com.bingoinnovation.redemptiontester.domain.redemption.RequestIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * Creates the app's long-lived objects once (manual dependency injection). One instance
 * lives in [com.bingoinnovation.redemptiontester.RedemptionTesterApplication].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Outlives screens, so a request in flight is always recorded. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True only in debug builds: plain-HTTP Portal addresses are accepted for local testing. */
    val allowCleartextServers: Boolean = BuildConfig.DEBUG

    val versionName: String = BuildConfig.VERSION_NAME

    val requestIds: RequestIdGenerator = RandomRequestIdGenerator

    private val settingsStore: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = JsonDataStoreSerializer(AppSettings.serializer(), AppSettings()),
        corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
        produceFile = { appContext.dataStoreFile(SETTINGS_FILE) },
    )

    // If the Keystore key is lost (for example after a reset), the vault cannot be
    // decrypted and starts empty; the Caller label must then be scanned again.
    private val vaultStore: DataStore<Vault> = DataStoreFactory.create(
        serializer = JsonDataStoreSerializer(Vault.serializer(), Vault(), KeystoreDataCipher(VAULT_KEY_ALIAS)),
        corruptionHandler = ReplaceFileCorruptionHandler { Vault() },
        produceFile = { appContext.dataStoreFile(VAULT_FILE) },
    )

    val settingsRepository = SettingsRepository(settingsStore)
    val callerRepository = CallerRepository(vaultStore)
    val historyRepository = HistoryRepository(vaultStore)

    private val portalClient = PortalClient(
        httpClient = PortalHttpClientFactory.create(),
        json = Json { ignoreUnknownKeys = true },
        userAgent = "RedemptionTester/$versionName (Android)",
    )

    val portalRepository = PortalRepository(
        gateway = portalClient,
        callers = callerRepository,
        settings = settingsRepository,
        history = historyRepository,
        externalScope = applicationScope,
    )

    private companion object {
        const val SETTINGS_FILE = "settings.json"
        const val VAULT_FILE = "vault.bin"
        const val VAULT_KEY_ALIAS = "redemption_tester_vault_v1"
    }
}
