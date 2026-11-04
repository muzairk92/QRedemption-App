package com.bingoinnovation.redemptiontester.ui.util

import java.net.URI
import java.net.URISyntaxException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting. BingoCommerce shows dates as MM/DD/YYYY, so this app does too;
 * times use the same US 12-hour style.
 */
object DisplayFormats {

    private val DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val DATE_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US)

    /** `2026-11-09` → `11/09/2026`. Returns the input unchanged if it is not a date. */
    fun sessionDate(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DATE)
    } catch (e: DateTimeException) {
        isoDate
    }

    /** `17:00:00` → `5:00 PM`, or null when there is no valid time. */
    fun sessionTime(localTime: String?): String? = localTime?.let {
        try {
            LocalTime.parse(it).format(TIME)
        } catch (e: DateTimeException) {
            null
        }
    }

    /** A history timestamp in the phone's time zone, such as `11/03/2026 3:04 PM`. */
    fun timestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DATE_TIME)

    /** `https://staging.bingoinnovation.com` → `staging.bingoinnovation.com`. */
    fun host(baseUrl: String): String = try {
        val uri = URI(baseUrl)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        (uri.host ?: baseUrl) + port
    } catch (e: URISyntaxException) {
        baseUrl
    }
}
