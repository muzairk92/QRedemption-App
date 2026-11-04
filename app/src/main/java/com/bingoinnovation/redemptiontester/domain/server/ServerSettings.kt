package com.bingoinnovation.redemptiontester.domain.server

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

object PortalServers {
    const val STAGING_BASE_URL = "https://staging.bingoinnovation.com"
}

@Serializable
enum class ServerPreset {
    STAGING,
    CUSTOM,
}

/** Which Portal this app talks to. [customBaseUrl] is only ever saved after validation. */
@Serializable
data class ServerSettings(
    val preset: ServerPreset = ServerPreset.STAGING,
    val customBaseUrl: String = "",
) {
    /** The Portal origin for requests, or null when Custom is selected without a saved address. */
    val baseUrl: String?
        get() = when (preset) {
            ServerPreset.STAGING -> PortalServers.STAGING_BASE_URL
            ServerPreset.CUSTOM -> customBaseUrl.ifBlank { null }
        }
}

object BaseUrlRules {

    sealed interface Result {
        /** [baseUrl] is normalised: lower-case scheme and host, no trailing slash. */
        data class Valid(val baseUrl: String) : Result

        data object Empty : Result

        data object Malformed : Result

        data object MissingScheme : Result

        data object CleartextNotAllowed : Result

        data object ContainsCredentials : Result

        data object ContainsQueryOrFragment : Result
    }

    /**
     * Validates a Portal address such as `https://staging.bingoinnovation.com` or, in
     * debug builds, `http://192.168.1.20:8000`. A path is allowed for a Portal installed
     * below the site root.
     */
    fun validate(raw: String, allowCleartext: Boolean): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Empty

        val uri = try {
            URI(text)
        } catch (e: URISyntaxException) {
            return Result.Malformed
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return Result.MissingScheme
        if (scheme == "http" && !allowCleartext) return Result.CleartextNotAllowed
        val host = uri.host ?: return Result.Malformed
        if (uri.rawUserInfo != null) return Result.ContainsCredentials
        if (uri.rawQuery != null || uri.rawFragment != null) return Result.ContainsQueryOrFragment

        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return Result.Valid("$scheme://${host.lowercase()}$port$path")
    }
}
