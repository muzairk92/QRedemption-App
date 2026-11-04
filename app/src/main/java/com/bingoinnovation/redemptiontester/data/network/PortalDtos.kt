package com.bingoinnovation.redemptiontester.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire formats of the Portal Caller API. Field names follow the Portal contract in
 * bingo_portal/docs/BINGOCOMMERCE_INTEGRATION.md ("Caller QR redemption relay").
 */

/** Body of `POST /api/caller/redemptions`. Exactly these five fields are allowed. */
@Serializable
internal data class RedemptionRequestDto(
    @SerialName("CallerSN") val callerSn: String,
    @SerialName("APIKey") val apiKey: String,
    @SerialName("qr") val qr: String,
    @SerialName("player_tablet_id") val playerTabletId: String,
    @SerialName("request_id") val requestId: String,
) {
    override fun toString(): String = "RedemptionRequestDto(callerSn=$callerSn, requestId=$requestId)"
}

/** HTTP 200 body. Nullable fields have no default, so their key must be present. */
@Serializable
internal data class RedemptionSuccessDto(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("request_id") val requestId: String,
    @SerialName("player_tablet_id") val playerTabletId: String,
    @SerialName("redemption_id") val redemptionId: String,
    @SerialName("recovered") val recovered: Boolean,
    @SerialName("state") val state: String,
    @SerialName("session") val session: SessionDto,
    @SerialName("package") val ticketPackage: PackageDto,
    @SerialName("digital_faces_by_game_type") val faces: List<GameTypeFacesDto>,
)

@Serializable
internal data class SessionDto(
    @SerialName("commerce_session_id") val commerceSessionId: Long,
    @SerialName("name") val name: String,
    @SerialName("date") val date: String,
    @SerialName("start_time_local") val startTimeLocal: String?,
    @SerialName("timezone_name") val timezoneName: String?,
    @SerialName("starts_at_utc") val startsAtUtc: String?,
)

@Serializable
internal data class PackageDto(
    @SerialName("commerce_package_id") val commercePackageId: Long,
    @SerialName("name") val name: String,
)

@Serializable
internal data class GameTypeFacesDto(
    @SerialName("portal_game_type_id") val portalGameTypeId: Long,
    @SerialName("faces") val faces: Long,
    // Added by the Portal after it validates Commerce's answer; may be null.
    @SerialName("game_type_name") val gameTypeName: String? = null,
)

/** Every non-200 answer from the redemption relay. */
@Serializable
internal data class ErrorEnvelopeDto(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("error") val error: ErrorDto,
)

@Serializable
internal data class ErrorDto(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("retryable") val retryable: Boolean,
)

/** Body of `POST /api/caller/verify-credentials`. */
@Serializable
internal data class VerifyCredentialsRequestDto(
    @SerialName("CallerID") val callerId: String,
    @SerialName("APIKey") val apiKey: String,
) {
    override fun toString(): String = "VerifyCredentialsRequestDto(callerId=$callerId)"
}

@Serializable
internal data class VerifyCredentialsResponseDto(
    @SerialName("valid") val valid: Boolean,
    @SerialName("message") val message: String? = null,
)
