package com.bingoinnovation.redemptiontester.domain.ticket

/**
 * A scanned BingoCommerce digital ticket: `BC1.<Hall site ID>.<64 lowercase hex token>`.
 *
 * The QR is an opaque bearer credential. It is sent to the server exactly as scanned;
 * the server, not this value, decides whether the ticket is valid. [toString] hides
 * most of the token so the full ticket never reaches a log.
 */
class TicketQr private constructor(
    /** The exact scanned text. */
    val value: String,
    /** The WordPress site ID of the Hall that sold the ticket. Only a hint to the server. */
    val siteId: String,
    private val token: String,
) {
    /** The first and last four characters of the token, for display. */
    val maskedToken: String
        get() = token.take(VISIBLE_CHARACTERS) + "…" + token.takeLast(VISIBLE_CHARACTERS)

    override fun equals(other: Any?): Boolean = other is TicketQr && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "TicketQr(siteId=$siteId, token=$maskedToken)"

    companion object {
        private const val VISIBLE_CHARACTERS = 4

        // Same format the Commerce redemption contract accepts (site ID of 1–19 digits).
        private val FORMAT = Regex("BC1\\.([1-9][0-9]{0,18})\\.([a-f0-9]{64})")

        /** Returns the ticket, or null when [text] is not exactly a BC1 ticket reference. */
        fun parse(text: String): TicketQr? {
            val match = FORMAT.matchEntire(text) ?: return null
            return TicketQr(value = text, siteId = match.groupValues[1], token = match.groupValues[2])
        }
    }
}
