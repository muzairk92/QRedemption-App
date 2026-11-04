package com.bingoinnovation.redemptiontester.data.storage

import androidx.datastore.core.DataStore
import com.bingoinnovation.redemptiontester.domain.redemption.TabletIdRules
import com.bingoinnovation.redemptiontester.domain.server.ServerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/** Non-secret preferences. */
@Serializable
data class AppSettings(
    val server: ServerSettings = ServerSettings(),
    /** This phone's player tablet ID. Created on first launch, then only changed by the user. */
    val tabletId: String? = null,
)

class SettingsRepository(
    private val store: DataStore<AppSettings>,
    private val newTabletId: () -> String = { TabletIdRules.generate() },
) {
    val settings: Flow<AppSettings> = store.data

    suspend fun current(): AppSettings = store.data.first()

    suspend fun setServer(server: ServerSettings) {
        store.updateData { it.copy(server = server) }
    }

    suspend fun setTabletId(tabletId: String) {
        require(TabletIdRules.isValid(tabletId)) { "Invalid tablet ID." }
        store.updateData { it.copy(tabletId = tabletId) }
    }

    /** The saved tablet ID, creating a stable one the first time it is needed. */
    suspend fun ensureTabletId(): String {
        val updated = store.updateData { settings ->
            if (settings.tabletId == null) settings.copy(tabletId = newTabletId()) else settings
        }
        return checkNotNull(updated.tabletId)
    }
}
