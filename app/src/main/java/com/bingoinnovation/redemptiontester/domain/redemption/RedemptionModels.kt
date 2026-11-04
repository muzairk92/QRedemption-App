package com.bingoinnovation.redemptiontester.domain.redemption

import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One redemption request, as the Caller sends it to `POST /api/caller/redemptions`. */
data class RedemptionRequest(
    val credentials: CallerCredentials,
    val ticket: TicketQr,
    val tabletId: String,
    val requestId: String,
)

/** A successful redemption, as returned by the Portal. */
@Serializable
data class Redemption(
    val redemptionId: String,
    val requestId: String,
    val playerTabletId: String,
    /** True when the server recognised a retry of an already accepted request. */
    val recovered: Boolean,
    val session: TicketSession,
    val ticketPackage: TicketPackage,
    val games: List<GameTypeFaces>,
) {
    val totalFaces: Long
        get() = games.sumOf { it.faces }

    /**
     * True when [other] reports the same saved purchase. Game Type display names are
     * left out on purpose: the Portal looks them up again on every answer, so a rename
     * in the Portal legitimately changes them on a recovery.
     */
    fun hasSameFactsAs(other: Redemption): Boolean =
        redemptionId == other.redemptionId &&
            session == other.session &&
            ticketPackage == other.ticketPackage &&
            games.map { it.portalGameTypeId to it.faces } == other.games.map { it.portalGameTypeId to it.faces }
}

/** The exact dated Session the ticket was bought for. */
@Serializable
data class TicketSession(
    val commerceSessionId: Long,
    val name: String,
    /** `YYYY-MM-DD`. */
    val date: String,
    /** `HH:MM:SS` in the Hall's time zone, or null when the Session has no saved time. */
    val startTimeLocal: String?,
    /** IANA time zone such as `America/New_York`, or null. */
    val timezoneName: String?,
    /** `YYYY-MM-DDTHH:MM:SSZ`, or null. */
    val startsAtUtc: String?,
)

@Serializable
data class TicketPackage(
    val commercePackageId: Long,
    val name: String,
)

/** Digital faces for one Portal Game Type. Paper is never included. */
@Serializable
data class GameTypeFaces(
    val portalGameTypeId: Long,
    val faces: Long,
    /** Current Portal name for display only; null when the Portal could not supply it. */
    val displayName: String?,
)

/** Everything that can come back from one redemption request. */
@Serializable
sealed interface RedemptionOutcome {

    /** HTTP 200 with a redemption. [checks] compare the answer with the published contract. */
    @Serializable
    @SerialName("success")
    data class Success(
        val redemption: Redemption,
        val checks: List<ContractCheck>,
    ) : RedemptionOutcome {
        val allChecksPassed: Boolean
            get() = checks.all { it.passed }
    }

    /** A documented error answer: `{api_version, error:{code, message, retryable}}`. */
    @Serializable
    @SerialName("rejected")
    data class Rejected(
        val httpStatus: Int,
        val code: String,
        val message: String,
        val retryable: Boolean,
        val retryAfterSeconds: Long? = null,
    ) : RedemptionOutcome {
        val knownCode: RedemptionErrorCode?
            get() = RedemptionErrorCode.fromWire(code)

        /** False when the HTTP status differs from the one documented for [code]. */
        val statusMatchesCode: Boolean
            get() = knownCode?.httpStatus == httpStatus
    }

    /** An answer that does not follow the contract, such as an HTML error page. */
    @Serializable
    @SerialName("unexpected")
    data class UnexpectedResponse(
        val httpStatus: Int,
        val reason: UnexpectedReason,
        /** The start of the body with the API key and ticket removed. */
        val bodyPreview: String,
    ) : RedemptionOutcome

    /** No HTTP answer at all. */
    @Serializable
    @SerialName("transport")
    data class TransportFailure(
        val kind: TransportFailureKind,
    ) : RedemptionOutcome
}

/**
 * True when the server may have accepted the request without this app learning so.
 * The only safe next step is to resend the same `request_id`.
 */
val RedemptionOutcome?.needsRetryWithSameRequest: Boolean
    get() = when (this) {
        null -> true
        is RedemptionOutcome.Success -> false
        is RedemptionOutcome.Rejected -> retryable
        is RedemptionOutcome.UnexpectedResponse -> httpStatus >= 500
        is RedemptionOutcome.TransportFailure -> kind.outcomeUnknown
    }

@Serializable
data class ContractCheck(
    val rule: ContractRule,
    val passed: Boolean,
)

/** Checks applied to every successful answer, taken from the Portal and Commerce contract. */
@Serializable
enum class ContractRule {
    API_VERSION,
    REQUEST_ID_ECHOED,
    TABLET_ID_ECHOED,
    REDEMPTION_ID_FORMAT,
    STATE_REDEEMED,
    SESSION_VALID,
    PACKAGE_VALID,
    GAME_TYPES_VALID,
    NO_UNEXPECTED_FIELDS,

    /** Added only for a retry of a request that this app saw succeed before. */
    RECOVERY_MATCHES_ORIGINAL,
}

@Serializable
enum class UnexpectedReason {
    NOT_JSON,
    SUCCESS_SHAPE_INVALID,
    ERROR_SHAPE_INVALID,
    REDIRECT,
    BODY_TOO_LARGE,
}

@Serializable
enum class TransportFailureKind(
    /** True when the request may have reached the server before the failure. */
    val outcomeUnknown: Boolean,
) {
    TIMEOUT(outcomeUnknown = true),
    NETWORK_ERROR(outcomeUnknown = true),
    CONNECTION_FAILED(outcomeUnknown = false),
    HOST_NOT_FOUND(outcomeUnknown = false),
    TLS_FAILED(outcomeUnknown = false),
    CLEARTEXT_BLOCKED(outcomeUnknown = false),
    INVALID_ADDRESS(outcomeUnknown = false),
}
