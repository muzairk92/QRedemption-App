package com.bingoinnovation.redemptiontester.domain.ticket

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TicketQrTest {

    private val token = "0123456789abcdef".repeat(4)

    @Test
    fun parse_acceptsTicketExactlyAsScanned() {
        val ticket = TicketQr.parse("BC1.870.$token")

        assertThat(ticket).isNotNull()
        assertThat(ticket!!.value).isEqualTo("BC1.870.$token")
        assertThat(ticket.siteId).isEqualTo("870")
    }

    @Test
    fun parse_acceptsLongestAllowedSiteId() {
        assertThat(TicketQr.parse("BC1.${"9".repeat(19)}.$token")).isNotNull()
        assertThat(TicketQr.parse("BC1.${"9".repeat(20)}.$token")).isNull()
    }

    @Test
    fun parse_rejectsAnythingThatIsNotExactlyATicket() {
        val invalid = listOf(
            "",
            "BC1.0.$token", // site ID cannot start with zero
            "BC1.012.$token",
            "BC1.12.${token.uppercase()}", // token is lowercase hex only
            "BC1.12.${token.dropLast(1)}",
            "BC1.12.${token}0",
            "BC2.12.$token",
            "bc1.12.$token",
            " BC1.12.$token",
            "BC1.12.$token\n",
            "BC1.12.$token ",
            """{"pid":"201-4827193","ak":"$token"}""",
        )
        invalid.forEach { assertThat(TicketQr.parse(it)).isNull() }
    }

    @Test
    fun toString_andMaskedToken_neverRevealTheWholeToken() {
        val ticket = TicketQr.parse("BC1.12.$token")!!

        assertThat(ticket.maskedToken).isEqualTo("0123…cdef")
        assertThat(ticket.toString()).doesNotContain(token)
    }

    @Test
    fun equality_followsTheExactText() {
        assertThat(TicketQr.parse("BC1.12.$token")).isEqualTo(TicketQr.parse("BC1.12.$token"))
        assertThat(TicketQr.parse("BC1.12.$token")).isNotEqualTo(TicketQr.parse("BC1.13.$token"))
    }
}
