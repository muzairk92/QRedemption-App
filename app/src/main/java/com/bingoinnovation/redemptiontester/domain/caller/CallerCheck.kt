package com.bingoinnovation.redemptiontester.domain.caller

import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind

/**
 * Result of the Portal's read-only `POST /api/caller/verify-credentials` check.
 *
 * That endpoint confirms the Caller ID and API key belong to an active Caller. It does
 * not check that the Caller is assigned to a Hall, which redemption also requires.
 */
sealed interface CallerCheck {
    data object Valid : CallerCheck

    data object Rejected : CallerCheck

    /** This Portal does not have the endpoint (HTTP 404 or 405). */
    data object NotAvailable : CallerCheck

    data class RateLimited(val retryAfterSeconds: Long?) : CallerCheck

    data class UnexpectedStatus(val httpStatus: Int) : CallerCheck

    data class Failed(val kind: TransportFailureKind) : CallerCheck
}
