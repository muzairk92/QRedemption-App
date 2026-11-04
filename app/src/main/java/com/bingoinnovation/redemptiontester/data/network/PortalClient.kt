package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.caller.CallerCheck
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The two Portal Caller endpoints this app uses. */
interface PortalGateway {

    /** `POST /api/caller/redemptions`. Never throws for network or server problems. */
    suspend fun redeem(baseUrl: String, request: RedemptionRequest): RedemptionOutcome

    /** `POST /api/caller/verify-credentials`: read-only, changes nothing on the server. */
    suspend fun verifyCaller(baseUrl: String, credentials: CallerCredentials): CallerCheck
}

class PortalClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val userAgent: String,
) : PortalGateway {

    private val interpreter = RedemptionResponseInterpreter(json)

    override suspend fun redeem(baseUrl: String, request: RedemptionRequest): RedemptionOutcome {
        val url = endpoint(baseUrl, REDEMPTIONS_PATH)
            ?: return RedemptionOutcome.TransportFailure(TransportFailureKind.INVALID_ADDRESS)
        val payload = RedemptionRequestDto(
            callerSn = request.credentials.callerId,
            apiKey = request.credentials.apiKey,
            qr = request.ticket.value,
            playerTabletId = request.tabletId,
            requestId = request.requestId,
        )
        // Credentials travel only in the JSON body, never in the address.
        val body = json.encodeToString(RedemptionRequestDto.serializer(), payload)
        return when (val result = httpClient.exchange(post(url, body), MAX_RESPONSE_BYTES)) {
            is HttpResult.Completed -> interpreter.interpret(result, request)
            is HttpResult.Failed -> RedemptionOutcome.TransportFailure(result.kind)
        }
    }

    override suspend fun verifyCaller(baseUrl: String, credentials: CallerCredentials): CallerCheck {
        val url = endpoint(baseUrl, VERIFY_PATH)
            ?: return CallerCheck.Failed(TransportFailureKind.INVALID_ADDRESS)
        val payload = VerifyCredentialsRequestDto(callerId = credentials.callerId, apiKey = credentials.apiKey)
        val body = json.encodeToString(VerifyCredentialsRequestDto.serializer(), payload)
        return when (val result = httpClient.exchange(post(url, body), MAX_VERIFY_RESPONSE_BYTES)) {
            is HttpResult.Failed -> CallerCheck.Failed(result.kind)
            is HttpResult.Completed -> when (result.status) {
                HTTP_OK -> if (isValidAnswer(result.body)) CallerCheck.Valid else CallerCheck.UnexpectedStatus(HTTP_OK)
                // The endpoint answers 401 for a wrong pair and 422 for a malformed one.
                HTTP_UNAUTHORIZED, HTTP_UNPROCESSABLE -> CallerCheck.Rejected
                HTTP_NOT_FOUND, HTTP_METHOD_NOT_ALLOWED -> CallerCheck.NotAvailable
                HTTP_TOO_MANY_REQUESTS -> CallerCheck.RateLimited(result.retryAfterSeconds)
                else -> CallerCheck.UnexpectedStatus(result.status)
            }
        }
    }

    private fun post(url: HttpUrl, jsonBody: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", userAgent)
        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun isValidAnswer(body: String?): Boolean = try {
        body != null && json.decodeFromString(VerifyCredentialsResponseDto.serializer(), body).valid
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun endpoint(baseUrl: String, path: String): HttpUrl? =
        baseUrl.toHttpUrlOrNull()?.newBuilder()?.addPathSegments(path)?.build()

    private companion object {
        const val REDEMPTIONS_PATH = "api/caller/redemptions"
        const val VERIFY_PATH = "api/caller/verify-credentials"

        // Commerce caps its answer at 262,144 bytes; the Portal then adds Game Type names.
        const val MAX_RESPONSE_BYTES = 512L * 1024
        const val MAX_VERIFY_RESPONSE_BYTES = 16L * 1024

        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_UNPROCESSABLE = 422
        const val HTTP_TOO_MANY_REQUESTS = 429

        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
