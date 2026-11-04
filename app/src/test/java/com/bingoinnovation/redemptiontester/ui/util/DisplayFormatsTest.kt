package com.bingoinnovation.redemptiontester.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class DisplayFormatsTest {

    @Test
    fun sessionDate_usesMmDdYyyy() {
        assertThat(DisplayFormats.sessionDate("2026-11-09")).isEqualTo("11/09/2026")
        assertThat(DisplayFormats.sessionDate("not a date")).isEqualTo("not a date")
    }

    @Test
    fun sessionTime_usesTwelveHourClock() {
        assertThat(DisplayFormats.sessionTime("17:00:00")).isEqualTo("5:00 PM")
        assertThat(DisplayFormats.sessionTime("09:05:00")).isEqualTo("9:05 AM")
        assertThat(DisplayFormats.sessionTime(null)).isNull()
        assertThat(DisplayFormats.sessionTime("25:00:00")).isNull()
    }

    @Test
    fun timestamp_usesTheGivenZone() {
        // 2026-11-03T20:01:00Z is 3:01 PM in New York (EST, UTC-5).
        val millis = 1_793_736_060_000L
        assertThat(DisplayFormats.timestamp(millis, ZoneId.of("America/New_York"))).isEqualTo("11/03/2026 3:01 PM")
    }

    @Test
    fun host_dropsSchemeButKeepsPort() {
        assertThat(DisplayFormats.host("https://staging.bingoinnovation.com")).isEqualTo("staging.bingoinnovation.com")
        assertThat(DisplayFormats.host("http://192.168.1.20:8000")).isEqualTo("192.168.1.20:8000")
    }
}
