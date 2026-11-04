package com.bingoinnovation.redemptiontester.domain.caller

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallerQrParserTest {

    private val key = "0123456789abcdef".repeat(4)

    @Test
    fun parse_readsThePortalOemLabel() {
        // Exactly what ProductBatchPdfService encodes: json_encode(['pid' => ..., 'ak' => ...]).
        val result = CallerQrParser.parse("""{"pid":"201-4827193","ak":"$key"}""")

        assertThat(result).isEqualTo(CallerQrParser.Result.Valid(CallerCredentials("201-4827193", key)))
    }

    @Test
    fun parse_ignoresExtraFields_andNormalisesCase() {
        val result = CallerQrParser.parse("""{"v":2,"pid":"ab1-4827193","ak":"${key.uppercase()}","x":true}""")

        assertThat(result).isEqualTo(CallerQrParser.Result.Valid(CallerCredentials("AB1-4827193", key)))
    }

    @Test
    fun parse_recognisesATicketScannedByMistake() {
        assertThat(CallerQrParser.parse("BC1.12.${"ab".repeat(32)}"))
            .isEqualTo(CallerQrParser.Result.TicketInsteadOfCaller)
    }

    @Test
    fun parse_rejectsOtherContent() {
        listOf(
            "201-4827193",
            "https://example.com",
            "[]",
            """{"pid":"201-4827193"}""",
            """{"ak":"$key"}""",
            """{"pid":2014827193,"ak":"$key"}""",
            """{"pid":null,"ak":"$key"}""",
            "{not json",
        ).forEach { assertThat(CallerQrParser.parse(it)).isEqualTo(CallerQrParser.Result.NotACallerLabel) }
    }

    @Test
    fun parse_reportsWhichFieldIsInvalid() {
        assertThat(CallerQrParser.parse("""{"pid":"201 4827193","ak":"$key"}"""))
            .isEqualTo(CallerQrParser.Result.InvalidCallerId)
        assertThat(CallerQrParser.parse("""{"pid":"${"A".repeat(21)}","ak":"$key"}"""))
            .isEqualTo(CallerQrParser.Result.InvalidCallerId)
        assertThat(CallerQrParser.parse("""{"pid":"201-4827193","ak":"${key.dropLast(1)}"}"""))
            .isEqualTo(CallerQrParser.Result.InvalidApiKey)
        assertThat(CallerQrParser.parse("""{"pid":"201-4827193","ak":"${key.dropLast(1)}g"}"""))
            .isEqualTo(CallerQrParser.Result.InvalidApiKey)
    }

    @Test
    fun credentials_neverPrintTheApiKey() {
        val credentials = CallerCredentials("201-4827193", key)

        assertThat(credentials.toString()).doesNotContain(key)
        assertThat(credentials.maskedApiKey).endsWith("cdef")
        assertThat(credentials.maskedApiKey).doesNotContain(key.take(8))
    }

    @Test
    fun manualEntry_isTrimmedAndValidated() {
        val valid = CallerCredentialRules.validate("  201-4827193 ", " $key\n")
        assertThat(valid).isEqualTo(CallerCredentialRules.Validation.Valid(CallerCredentials("201-4827193", key)))

        val invalid = CallerCredentialRules.validate("", "short")
        assertThat(invalid).isEqualTo(CallerCredentialRules.Validation.Invalid(callerIdInvalid = true, apiKeyInvalid = true))
    }
}
