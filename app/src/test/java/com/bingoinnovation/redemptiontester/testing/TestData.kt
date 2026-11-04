package com.bingoinnovation.redemptiontester.testing

import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.ContractCheck
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.GameTypeFaces
import com.bingoinnovation.redemptiontester.domain.redemption.Redemption
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import com.bingoinnovation.redemptiontester.domain.redemption.TicketPackage
import com.bingoinnovation.redemptiontester.domain.redemption.TicketSession
import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr

/**
 * Fixtures modelled on the real staging answer of 2026-11-03 (order 183, Hall 7):
 * Session "Small Frames — 17:00" on 2026-11-09, Package 1, Game Types 10/12/14.
 * All credentials and tickets here are made up.
 */
object TestData {
    const val CALLER_ID = "201-4827193"
    val API_KEY = "0123456789abcdef".repeat(4)
    val TICKET_TEXT = "BC1.12." + "ab".repeat(32)
    const val TABLET_ID = "staging-tablet-A"
    const val OTHER_TABLET_ID = "staging-tablet-B"
    const val REQUEST_ID = "8dd0aac6-65fb-4ecf-9a49-786951473221"
    const val REDEMPTION_ID = "7f8f8c92-1916-4ee0-9455-2473ae9b46e7"

    val credentials = CallerCredentials(callerId = CALLER_ID, apiKey = API_KEY)
    val ticket: TicketQr = checkNotNull(TicketQr.parse(TICKET_TEXT))

    fun request(tabletId: String = TABLET_ID, requestId: String = REQUEST_ID) =
        RedemptionRequest(credentials = credentials, ticket = ticket, tabletId = tabletId, requestId = requestId)

    fun successJson(
        requestId: String = REQUEST_ID,
        tabletId: String = TABLET_ID,
        recovered: Boolean = false,
        redemptionId: String = REDEMPTION_ID,
        extraTopLevel: String = "",
    ): String = """
        {
          "api_version": 1,
          "request_id": "$requestId",
          "player_tablet_id": "$tabletId",
          "redemption_id": "$redemptionId",
          "recovered": $recovered,
          "state": "redeemed",
          "session": {
            "commerce_session_id": 1,
            "name": "Small Frames — 17:00",
            "date": "2026-11-09",
            "start_time_local": "17:00:00",
            "timezone_name": "America/New_York",
            "starts_at_utc": "2026-11-09T22:00:00Z"
          },
          "package": { "commerce_package_id": 1, "name": "Package 1" },
          "digital_faces_by_game_type": [
            { "portal_game_type_id": 10, "faces": 24, "game_type_name": "Regular" },
            { "portal_game_type_id": 12, "faces": 18, "game_type_name": "Late Bird" },
            { "portal_game_type_id": 14, "faces": 4, "game_type_name": null }
          ]$extraTopLevel
        }
    """.trimIndent()

    fun errorJson(code: String, message: String, retryable: Boolean): String =
        """{"api_version":1,"error":{"code":"$code","message":"$message","retryable":$retryable}}"""

    fun redemption(
        recovered: Boolean = false,
        redemptionId: String = REDEMPTION_ID,
        requestId: String = REQUEST_ID,
        tabletId: String = TABLET_ID,
    ) = Redemption(
        redemptionId = redemptionId,
        requestId = requestId,
        playerTabletId = tabletId,
        recovered = recovered,
        session = TicketSession(
            commerceSessionId = 1,
            name = "Small Frames — 17:00",
            date = "2026-11-09",
            startTimeLocal = "17:00:00",
            timezoneName = "America/New_York",
            startsAtUtc = "2026-11-09T22:00:00Z",
        ),
        ticketPackage = TicketPackage(commercePackageId = 1, name = "Package 1"),
        games = listOf(
            GameTypeFaces(portalGameTypeId = 10, faces = 24, displayName = "Regular"),
            GameTypeFaces(portalGameTypeId = 12, faces = 18, displayName = "Late Bird"),
            GameTypeFaces(portalGameTypeId = 14, faces = 4, displayName = null),
        ),
    )

    fun success(recovered: Boolean = false, redemptionId: String = REDEMPTION_ID, tabletId: String = TABLET_ID) =
        RedemptionOutcome.Success(
            redemption = redemption(recovered = recovered, redemptionId = redemptionId, tabletId = tabletId),
            checks = ContractRule.entries
                .filter { it != ContractRule.RECOVERY_MATCHES_ORIGINAL }
                .map { ContractCheck(it, passed = true) },
        )

    fun rejected(code: String, status: Int, retryable: Boolean = false) =
        RedemptionOutcome.Rejected(httpStatus = status, code = code, message = "msg", retryable = retryable)
}
