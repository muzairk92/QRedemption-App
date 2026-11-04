package com.bingoinnovation.redemptiontester.domain.redemption

/**
 * Every error code the Portal relay can return (`RedemptionContract::ERRORS` in the
 * Portal), with its documented HTTP status.
 */
enum class RedemptionErrorCode(
    val wire: String,
    val httpStatus: Int,
) {
    INVALID_REQUEST("invalid_request", 400),
    MALFORMED_QR("malformed_qr", 400),
    AUTHENTICATION_REQUIRED("authentication_required", 401),
    CALLER_NOT_AUTHORIZED("caller_not_authorized", 403),
    WRONG_HALL("wrong_hall", 403),
    TICKET_NOT_FOUND("ticket_not_found", 404),
    ALREADY_REDEEMED("already_redeemed", 409),
    REQUEST_ID_CONFLICT("request_id_conflict", 409),
    WRONG_SESSION("wrong_session", 409),
    ELECTRONIC_LIMIT_EXCEEDED("electronic_limit_exceeded", 409),
    TICKET_VOIDED("ticket_voided", 410),
    SAVED_PURCHASE_INVALID("saved_purchase_invalid", 422),
    RATE_LIMITED("rate_limited", 429),
    MAPPING_UNAVAILABLE("mapping_unavailable", 503),
    VERIFICATION_UNAVAILABLE("verification_unavailable", 503),
    WRITE_OUTCOME_UNKNOWN("write_outcome_unknown", 503),
    ;

    companion object {
        fun fromWire(code: String): RedemptionErrorCode? = entries.firstOrNull { it.wire == code }
    }
}
