package com.bingoinnovation.redemptiontester.domain.caller

import com.bingoinnovation.redemptiontester.domain.ticket.TicketQr
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the Caller label QR printed by the Portal on Product ID batch sheets (the
 * "OEM / pre-authorized" label). Its content is the JSON object
 * `{"pid":"<Caller ID>","ak":"<API key>"}`; extra fields are ignored so the format can
 * grow, as the Portal intends.
 */
object CallerQrParser {

    sealed interface Result {
        data class Valid(val credentials: CallerCredentials) : Result

        /** A customer ticket was scanned where a Caller label was expected. */
        data object TicketInsteadOfCaller : Result

        data object NotACallerLabel : Result

        data object InvalidCallerId : Result

        data object InvalidApiKey : Result
    }

    fun parse(text: String): Result {
        if (TicketQr.parse(text) != null) return Result.TicketInsteadOfCaller

        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return Result.NotACallerLabel
        }
        val label = element as? JsonObject ?: return Result.NotACallerLabel
        val callerId = label.stringOrNull("pid") ?: return Result.NotACallerLabel
        val apiKey = label.stringOrNull("ak") ?: return Result.NotACallerLabel

        return when (val validation = CallerCredentialRules.validate(callerId, apiKey)) {
            is CallerCredentialRules.Validation.Valid -> Result.Valid(validation.credentials)
            is CallerCredentialRules.Validation.Invalid ->
                if (validation.callerIdInvalid) Result.InvalidCallerId else Result.InvalidApiKey
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
