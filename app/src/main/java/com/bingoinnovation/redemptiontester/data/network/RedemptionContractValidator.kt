package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.redemption.ContractCheck
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import com.bingoinnovation.redemptiontester.domain.redemption.RequestIdRules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * Compares a successful redemption answer with the published contract. Works on the raw
 * JSON so that it also catches problems a lenient decoder would hide, such as an ID sent
 * as a string or an extra field.
 */
internal object RedemptionContractValidator {

    private val TOP_LEVEL_KEYS = setOf(
        "api_version", "request_id", "player_tablet_id", "redemption_id",
        "recovered", "state", "session", "package", "digital_faces_by_game_type",
    )
    private val SESSION_KEYS = setOf(
        "commerce_session_id", "name", "date", "start_time_local", "timezone_name", "starts_at_utc",
    )
    private val PACKAGE_KEYS = setOf("commerce_package_id", "name")
    private val GAME_TYPE_REQUIRED_KEYS = setOf("portal_game_type_id", "faces")
    private const val GAME_TYPE_NAME_KEY = "game_type_name"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    private val DATE_SHAPE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TIME_SHAPE = Regex("\\d{2}:\\d{2}:\\d{2}")
    private val UTC_SHAPE = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")

    fun check(root: JsonObject, request: RedemptionRequest): List<ContractCheck> = listOf(
        ContractCheck(ContractRule.API_VERSION, root["api_version"].isJsonInteger(expected = 1)),
        ContractCheck(ContractRule.REQUEST_ID_ECHOED, root["request_id"].stringValue() == request.requestId),
        ContractCheck(ContractRule.TABLET_ID_ECHOED, root["player_tablet_id"].stringValue() == request.tabletId),
        ContractCheck(
            ContractRule.REDEMPTION_ID_FORMAT,
            root["redemption_id"].stringValue()?.let(RequestIdRules::isValid) == true,
        ),
        ContractCheck(
            ContractRule.STATE_REDEEMED,
            root["state"].stringValue() == "redeemed" && root["recovered"].isJsonBoolean(),
        ),
        ContractCheck(ContractRule.SESSION_VALID, isValidSession(root["session"])),
        ContractCheck(ContractRule.PACKAGE_VALID, isValidPackage(root["package"])),
        ContractCheck(ContractRule.GAME_TYPES_VALID, isValidGameTypes(root["digital_faces_by_game_type"])),
        ContractCheck(ContractRule.NO_UNEXPECTED_FIELDS, hasExactFields(root)),
    )

    private fun isValidSession(element: JsonElement?): Boolean {
        val session = element as? JsonObject ?: return false
        val time = session["start_time_local"]
        val zone = session["timezone_name"]
        val utc = session["starts_at_utc"]
        val timing = listOf(time, zone, utc)
        // Commerce saves the start time, time zone and UTC instant together, or none of them.
        val timingConsistent = timing.all { it is JsonNull } || timing.none { it is JsonNull }

        return session["commerce_session_id"].isPositiveJsonInteger() &&
            session["name"].stringValue()?.isNotBlank() == true &&
            session["date"].stringValue()?.let(::isStrictDate) == true &&
            (time is JsonNull || time.stringValue()?.let(::isStrictTime) == true) &&
            (zone is JsonNull || zone.stringValue()?.let(::isKnownTimeZone) == true) &&
            (utc is JsonNull || utc.stringValue()?.let(::isStrictUtc) == true) &&
            timingConsistent
    }

    private fun isValidPackage(element: JsonElement?): Boolean {
        val ticketPackage = element as? JsonObject ?: return false
        return ticketPackage["commerce_package_id"].isPositiveJsonInteger() &&
            ticketPackage["name"].stringValue()?.isNotBlank() == true
    }

    private fun isValidGameTypes(element: JsonElement?): Boolean {
        val entries = element as? JsonArray ?: return false
        if (entries.isEmpty()) return false
        val seen = mutableSetOf<Long>()
        return entries.all { entry ->
            val gameType = entry as? JsonObject ?: return@all false
            val id = gameType["portal_game_type_id"]
            val name = gameType[GAME_TYPE_NAME_KEY]
            id.isPositiveJsonInteger() &&
                gameType["faces"].isPositiveJsonInteger() &&
                (name == null || name is JsonNull || name.stringValue() != null) &&
                seen.add((id as JsonPrimitive).longOrNull!!)
        }
    }

    private fun hasExactFields(root: JsonObject): Boolean {
        val session = root["session"] as? JsonObject
        val ticketPackage = root["package"] as? JsonObject
        val gameTypes = root["digital_faces_by_game_type"] as? JsonArray
        return root.keys == TOP_LEVEL_KEYS &&
            session?.keys == SESSION_KEYS &&
            ticketPackage?.keys == PACKAGE_KEYS &&
            gameTypes != null &&
            gameTypes.all { entry ->
                val keys = (entry as? JsonObject)?.keys ?: return@all false
                keys.containsAll(GAME_TYPE_REQUIRED_KEYS) && (keys - GAME_TYPE_REQUIRED_KEYS - GAME_TYPE_NAME_KEY).isEmpty()
            }
    }

    private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.isJsonInteger(expected: Long): Boolean =
        this is JsonPrimitive && !isString && longOrNull == expected

    private fun JsonElement?.isPositiveJsonInteger(): Boolean =
        this is JsonPrimitive && !isString && (longOrNull ?: 0L) > 0L

    private fun JsonElement?.isJsonBoolean(): Boolean =
        this is JsonPrimitive && !isString && booleanOrNull != null

    private fun isStrictDate(value: String): Boolean =
        DATE_SHAPE.matches(value) && parses { LocalDate.parse(value, DATE_FORMAT) }

    private fun isStrictTime(value: String): Boolean =
        TIME_SHAPE.matches(value) && parses { LocalTime.parse(value, TIME_FORMAT) }

    private fun isStrictUtc(value: String): Boolean =
        UTC_SHAPE.matches(value) && parses { Instant.parse(value) }

    private fun isKnownTimeZone(value: String): Boolean = value in ZoneId.getAvailableZoneIds()

    private inline fun parses(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: DateTimeParseException) {
        false
    }
}
