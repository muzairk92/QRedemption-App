package com.bingoinnovation.redemptiontester.ui.text

import androidx.annotation.StringRes
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionErrorCode
import com.bingoinnovation.redemptiontester.domain.redemption.TestScenario
import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import com.bingoinnovation.redemptiontester.domain.redemption.UnexpectedReason
import com.bingoinnovation.redemptiontester.domain.redemption.Verdict

/* Maps domain values to string resources, keeping all user-facing text in strings.xml. */

@StringRes
fun RedemptionErrorCode.titleRes(): Int = when (this) {
    RedemptionErrorCode.INVALID_REQUEST -> R.string.error_invalid_request_title
    RedemptionErrorCode.MALFORMED_QR -> R.string.error_malformed_qr_title
    RedemptionErrorCode.AUTHENTICATION_REQUIRED -> R.string.error_authentication_required_title
    RedemptionErrorCode.CALLER_NOT_AUTHORIZED -> R.string.error_caller_not_authorized_title
    RedemptionErrorCode.WRONG_HALL -> R.string.error_wrong_hall_title
    RedemptionErrorCode.TICKET_NOT_FOUND -> R.string.error_ticket_not_found_title
    RedemptionErrorCode.ALREADY_REDEEMED -> R.string.error_already_redeemed_title
    RedemptionErrorCode.REQUEST_ID_CONFLICT -> R.string.error_request_id_conflict_title
    RedemptionErrorCode.WRONG_SESSION -> R.string.error_wrong_session_title
    RedemptionErrorCode.ELECTRONIC_LIMIT_EXCEEDED -> R.string.error_electronic_limit_exceeded_title
    RedemptionErrorCode.TICKET_VOIDED -> R.string.error_ticket_voided_title
    RedemptionErrorCode.SAVED_PURCHASE_INVALID -> R.string.error_saved_purchase_invalid_title
    RedemptionErrorCode.RATE_LIMITED -> R.string.error_rate_limited_title
    RedemptionErrorCode.MAPPING_UNAVAILABLE -> R.string.error_mapping_unavailable_title
    RedemptionErrorCode.VERIFICATION_UNAVAILABLE -> R.string.error_verification_unavailable_title
    RedemptionErrorCode.WRITE_OUTCOME_UNKNOWN -> R.string.error_write_outcome_unknown_title
}

/** What the result means and what to do next. */
@StringRes
fun RedemptionErrorCode.guidanceRes(): Int = when (this) {
    RedemptionErrorCode.INVALID_REQUEST -> R.string.error_invalid_request_guidance
    RedemptionErrorCode.MALFORMED_QR -> R.string.error_malformed_qr_guidance
    RedemptionErrorCode.AUTHENTICATION_REQUIRED -> R.string.error_authentication_required_guidance
    RedemptionErrorCode.CALLER_NOT_AUTHORIZED -> R.string.error_caller_not_authorized_guidance
    RedemptionErrorCode.WRONG_HALL -> R.string.error_wrong_hall_guidance
    RedemptionErrorCode.TICKET_NOT_FOUND -> R.string.error_ticket_not_found_guidance
    RedemptionErrorCode.ALREADY_REDEEMED -> R.string.error_already_redeemed_guidance
    RedemptionErrorCode.REQUEST_ID_CONFLICT -> R.string.error_request_id_conflict_guidance
    RedemptionErrorCode.WRONG_SESSION -> R.string.error_wrong_session_guidance
    RedemptionErrorCode.ELECTRONIC_LIMIT_EXCEEDED -> R.string.error_electronic_limit_exceeded_guidance
    RedemptionErrorCode.TICKET_VOIDED -> R.string.error_ticket_voided_guidance
    RedemptionErrorCode.SAVED_PURCHASE_INVALID -> R.string.error_saved_purchase_invalid_guidance
    RedemptionErrorCode.RATE_LIMITED -> R.string.error_rate_limited_guidance
    RedemptionErrorCode.MAPPING_UNAVAILABLE -> R.string.error_mapping_unavailable_guidance
    RedemptionErrorCode.VERIFICATION_UNAVAILABLE -> R.string.error_verification_unavailable_guidance
    RedemptionErrorCode.WRITE_OUTCOME_UNKNOWN -> R.string.error_write_outcome_unknown_guidance
}

@StringRes
fun TransportFailureKind.titleRes(): Int = when (this) {
    TransportFailureKind.TIMEOUT -> R.string.transport_timeout_title
    TransportFailureKind.NETWORK_ERROR -> R.string.transport_network_error_title
    TransportFailureKind.CONNECTION_FAILED -> R.string.transport_connection_failed_title
    TransportFailureKind.HOST_NOT_FOUND -> R.string.transport_host_not_found_title
    TransportFailureKind.TLS_FAILED -> R.string.transport_tls_failed_title
    TransportFailureKind.CLEARTEXT_BLOCKED -> R.string.transport_cleartext_blocked_title
    TransportFailureKind.INVALID_ADDRESS -> R.string.transport_invalid_address_title
}

@StringRes
fun TransportFailureKind.guidanceRes(): Int =
    if (outcomeUnknown) R.string.transport_outcome_unknown_guidance else R.string.transport_not_sent_guidance

@StringRes
fun UnexpectedReason.titleRes(): Int = when (this) {
    UnexpectedReason.NOT_JSON -> R.string.unexpected_not_json
    UnexpectedReason.SUCCESS_SHAPE_INVALID -> R.string.unexpected_success_shape
    UnexpectedReason.ERROR_SHAPE_INVALID -> R.string.unexpected_error_shape
    UnexpectedReason.REDIRECT -> R.string.unexpected_redirect
    UnexpectedReason.BODY_TOO_LARGE -> R.string.unexpected_body_too_large
}

@StringRes
fun ContractRule.labelRes(): Int = when (this) {
    ContractRule.API_VERSION -> R.string.check_api_version
    ContractRule.REQUEST_ID_ECHOED -> R.string.check_request_id_echoed
    ContractRule.TABLET_ID_ECHOED -> R.string.check_tablet_id_echoed
    ContractRule.REDEMPTION_ID_FORMAT -> R.string.check_redemption_id_format
    ContractRule.STATE_REDEEMED -> R.string.check_state_redeemed
    ContractRule.SESSION_VALID -> R.string.check_session_valid
    ContractRule.PACKAGE_VALID -> R.string.check_package_valid
    ContractRule.GAME_TYPES_VALID -> R.string.check_game_types_valid
    ContractRule.NO_UNEXPECTED_FIELDS -> R.string.check_no_unexpected_fields
    ContractRule.RECOVERY_MATCHES_ORIGINAL -> R.string.check_recovery_matches_original
}

@StringRes
fun TestScenario.titleRes(): Int = when (this) {
    TestScenario.FIRST_REDEMPTION -> R.string.scenario_first_title
    TestScenario.RETRY_SAME_REQUEST -> R.string.scenario_retry_title
    TestScenario.NEW_REQUEST_SAME_TICKET -> R.string.scenario_new_request_title
    TestScenario.OTHER_TABLET_SAME_REQUEST -> R.string.scenario_other_tablet_title
}

@StringRes
fun TestScenario.expectationRes(): Int = when (this) {
    TestScenario.FIRST_REDEMPTION -> R.string.scenario_first_expected
    TestScenario.RETRY_SAME_REQUEST -> R.string.scenario_retry_expected
    TestScenario.NEW_REQUEST_SAME_TICKET -> R.string.scenario_new_request_expected
    TestScenario.OTHER_TABLET_SAME_REQUEST -> R.string.scenario_other_tablet_expected
}

@StringRes
fun Verdict.titleRes(): Int = when (this) {
    Verdict.AS_EXPECTED -> R.string.verdict_as_expected
    Verdict.NOT_AS_EXPECTED -> R.string.verdict_not_as_expected
    Verdict.NOT_CONFIRMED -> R.string.verdict_not_confirmed
}
