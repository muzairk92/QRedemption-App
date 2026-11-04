package com.bingoinnovation.redemptiontester.domain.server

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BaseUrlRulesTest {

    private fun valid(raw: String, allowCleartext: Boolean = false) =
        BaseUrlRules.validate(raw, allowCleartext)

    @Test
    fun normalisesValidAddresses() {
        assertThat(valid("https://staging.bingoinnovation.com/"))
            .isEqualTo(BaseUrlRules.Result.Valid("https://staging.bingoinnovation.com"))
        assertThat(valid("  HTTPS://Staging.BingoInnovation.com  "))
            .isEqualTo(BaseUrlRules.Result.Valid("https://staging.bingoinnovation.com"))
        assertThat(valid("https://example.com:8443/portal/"))
            .isEqualTo(BaseUrlRules.Result.Valid("https://example.com:8443/portal"))
    }

    @Test
    fun plainHttp_onlyWhenAllowed() {
        assertThat(valid("http://192.168.1.20:8000")).isEqualTo(BaseUrlRules.Result.CleartextNotAllowed)
        assertThat(valid("http://192.168.1.20:8000", allowCleartext = true))
            .isEqualTo(BaseUrlRules.Result.Valid("http://192.168.1.20:8000"))
    }

    @Test
    fun rejectsUnsafeOrIncompleteAddresses() {
        assertThat(valid("")).isEqualTo(BaseUrlRules.Result.Empty)
        assertThat(valid("   ")).isEqualTo(BaseUrlRules.Result.Empty)
        assertThat(valid("staging.bingoinnovation.com")).isEqualTo(BaseUrlRules.Result.MissingScheme)
        assertThat(valid("localhost:8000")).isEqualTo(BaseUrlRules.Result.MissingScheme)
        assertThat(valid("ftp://example.com")).isEqualTo(BaseUrlRules.Result.MissingScheme)
        assertThat(valid("https://user:secret@example.com")).isEqualTo(BaseUrlRules.Result.ContainsCredentials)
        assertThat(valid("https://example.com/?a=1")).isEqualTo(BaseUrlRules.Result.ContainsQueryOrFragment)
        assertThat(valid("https://example.com/#top")).isEqualTo(BaseUrlRules.Result.ContainsQueryOrFragment)
        assertThat(valid("https://exa mple.com")).isEqualTo(BaseUrlRules.Result.Malformed)
        assertThat(valid("https://")).isEqualTo(BaseUrlRules.Result.Malformed)
    }

    @Test
    fun serverSettings_resolveTheActiveAddress() {
        assertThat(ServerSettings().baseUrl).isEqualTo(PortalServers.STAGING_BASE_URL)
        assertThat(ServerSettings(ServerPreset.CUSTOM, "").baseUrl).isNull()
        assertThat(ServerSettings(ServerPreset.CUSTOM, "https://p.example").baseUrl).isEqualTo("https://p.example")
        assertThat(ServerSettings(ServerPreset.STAGING, "https://p.example").baseUrl)
            .isEqualTo(PortalServers.STAGING_BASE_URL)
    }
}
