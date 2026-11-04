package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.redemption.GameTypeFaces
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import com.bingoinnovation.redemptiontester.domain.redemption.TicketPackage
import com.bingoinnovation.redemptiontester.domain.redemption.TicketSession
import com.bingoinnovation.redemptiontester.domain.redemption.UnexpectedReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Turns one completed HTTP exchange with the redemption relay into a [RedemptionOutcome]. */
internal class RedemptionResponseInterpreter(private val json: Json) {

    fun interpret(response: HttpResult.Completed, request: RedemptionRequest): RedemptionOutcome {
        val status = response.status
        val body = response.body
            ?: return RedemptionOutcome.UnexpectedResponse(status, UnexpectedReason.BODY_TOO_LARGE, "")

        if (status in REDIRECT_STATUSES) {
            // Redirects are never followed: they could carry the credentials elsewhere.
            return RedemptionOutcome.UnexpectedResponse(
                status,
                UnexpectedReason.REDIRECT,
                preview(response.location.orEmpty(), request),
            )
        }

        val root = parseObject(body)
            ?: return RedemptionOutcome.UnexpectedResponse(status, UnexpectedReason.NOT_JSON, preview(body, request))

        return if (status == HTTP_OK) {
            success(root, body, request)
        } else {
            rejection(status, root, body, response.retryAfterSeconds, request)
        }
    }

    private fun success(root: JsonObject, body: String, request: RedemptionRequest): RedemptionOutcome {
        val dto = try {
            json.decodeFromJsonElement(RedemptionSuccessDto.serializer(), root)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException; so are bad numbers.
            return RedemptionOutcome.UnexpectedResponse(
                HTTP_OK,
                UnexpectedReason.SUCCESS_SHAPE_INVALID,
                preview(body, request),
            )
        }
        return RedemptionOutcome.Success(
            redemption = dto.toDomain(),
            checks = RedemptionContractValidator.check(root, request),
        )
    }

    private fun rejection(
        status: Int,
        root: JsonObject,
        body: String,
        retryAfterSeconds: Long?,
        request: RedemptionRequest,
    ): RedemptionOutcome {
        val dto = try {
            json.decodeFromJsonElement(ErrorEnvelopeDto.serializer(), root)
        } catch (e: IllegalArgumentException) {
            return RedemptionOutcome.UnexpectedResponse(
                status,
                UnexpectedReason.ERROR_SHAPE_INVALID,
                preview(body, request),
            )
        }
        return RedemptionOutcome.Rejected(
            httpStatus = status,
            code = dto.error.code,
            message = dto.error.message,
            retryable = dto.error.retryable,
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    private fun parseObject(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun RedemptionSuccessDto.toDomain() = Redemption(
        redemptionId = redemptionId,
        requestId = requestId,
        playerTabletId = playerTabletId,
        recovered = recovered,
        session = TicketSession(
            commerceSessionId = session.commerceSessionId,
            name = session.name,
            date = session.date,
            startTimeLocal = session.startTimeLocal,
            timezoneName = session.timezoneName,
            startsAtUtc = session.startsAtUtc,
        ),
        ticketPackage = TicketPackage(
            commercePackageId = ticketPackage.commercePackageId,
            name = ticketPackage.name,
        ),
        games = faces.map {
            GameTypeFaces(portalGameTypeId = it.portalGameTypeId, faces = it.faces, displayName = it.gameTypeName)
        },
    )

    private companion object {
        const val HTTP_OK = 200
        val REDIRECT_STATUSES = 300..399
    }
}

private const val MAX_PREVIEW_CHARACTERS = 600
private const val REDACTED = "[redacted]"

/** The start of a server body, safe to show and store: the API key and ticket are removed. */
internal fun preview(body: String, request: RedemptionRequest): String =
    body
        .replace(request.credentials.apiKey, REDACTED, ignoreCase = true)
        .replace(request.ticket.value, REDACTED)
        .take(MAX_PREVIEW_CHARACTERS)
