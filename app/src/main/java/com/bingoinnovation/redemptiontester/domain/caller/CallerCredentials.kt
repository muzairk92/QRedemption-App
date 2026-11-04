package com.bingoinnovation.redemptiontester.domain.caller

import kotlinx.serialization.Serializable

/**
 * A Caller's Portal identity: its Caller ID (the Portal also calls it CallerSN, AID or
 * Product ID) and its API key.
 *
 * The API key is a secret. [toString] masks it so it can never reach a log by accident.
 */
@Serializable
data class CallerCredentials(
    val callerId: String,
    val apiKey: String,
) {
    /** The API key with everything except its last four characters hidden. */
    val maskedApiKey: String
        get() = API_KEY_MASK + apiKey.takeLast(VISIBLE_KEY_CHARACTERS)

    override fun toString(): String = "CallerCredentials(callerId=$callerId, apiKey=$maskedApiKey)"
}

private const val API_KEY_MASK = "••••••••"
private const val VISIBLE_KEY_CHARACTERS = 4

/**
 * Input rules for Caller credentials, taken from the Portal:
 * - `caller_ids.caller_id` is a `VARCHAR(20)` of letters, digits and dashes
 *   (Product IDs look like `201-4827193`);
 * - API keys are 64 hexadecimal characters (`bin2hex(random_bytes(32))`).
 */
object CallerCredentialRules {

    private val CALLER_ID = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,19}")
    private val API_KEY = Regex("[A-Fa-f0-9]{64}")

    sealed interface Validation {
        data class Valid(val credentials: CallerCredentials) : Validation

        data class Invalid(
            val callerIdInvalid: Boolean,
            val apiKeyInvalid: Boolean,
        ) : Validation
    }

    fun isValidCallerId(value: String): Boolean = CALLER_ID.matches(value)

    fun isValidApiKey(value: String): Boolean = API_KEY.matches(value)

    /**
     * Validates typed or scanned values and normalises them.
     *
     * Surrounding whitespace is removed because neither value may contain spaces. The
     * Portal upper-cases the Caller ID and lower-cases the API key before comparing
     * them, so normalising here never changes how the server authenticates.
     */
    fun validate(callerId: String, apiKey: String): Validation {
        val id = callerId.trim()
        val key = apiKey.trim()
        val idValid = isValidCallerId(id)
        val keyValid = isValidApiKey(key)
        return if (idValid && keyValid) {
            Validation.Valid(CallerCredentials(callerId = id.uppercase(), apiKey = key.lowercase()))
        } else {
            Validation.Invalid(callerIdInvalid = !idValid, apiKeyInvalid = !keyValid)
        }
    }
}
