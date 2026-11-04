package com.bingoinnovation.redemptiontester.domain.redemption

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Rules for `player_tablet_id`, the identity of one physical player tablet.
 *
 * The server treats it as opaque: valid UTF-8, 1–128 bytes, no control characters.
 * Case and spaces are kept exactly, so this app never trims or changes it.
 */
object TabletIdRules {

    const val MAX_BYTES = 128

    private const val GENERATED_PREFIX = "tester-"
    private const val GENERATED_SUFFIX_LENGTH = 6
    private const val GENERATED_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val CONTROL_CHARACTER = Regex("\\p{Cc}")

    sealed interface Validation {
        data object Valid : Validation

        data object Empty : Validation

        data class TooLong(val bytes: Int) : Validation

        data object ControlCharacter : Validation

        data object InvalidText : Validation
    }

    fun validate(value: String): Validation = when {
        value.isEmpty() -> Validation.Empty
        hasUnpairedSurrogate(value) -> Validation.InvalidText
        CONTROL_CHARACTER.containsMatchIn(value) -> Validation.ControlCharacter
        else -> {
            val bytes = value.toByteArray(Charsets.UTF_8).size
            if (bytes > MAX_BYTES) Validation.TooLong(bytes) else Validation.Valid
        }
    }

    fun isValid(value: String): Boolean = validate(value) == Validation.Valid

    /** A random, readable ID such as `tester-k3x9qa`, used until the user sets their own. */
    fun generate(random: Random = SecureRandom().asKotlinRandom()): String =
        GENERATED_PREFIX + (1..GENERATED_SUFFIX_LENGTH)
            .map { GENERATED_ALPHABET[random.nextInt(GENERATED_ALPHABET.length)] }
            .joinToString("")

    // Unpaired UTF-16 surrogates cannot be encoded as valid UTF-8.
    private fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return true
                    index += 2
                }
                char.isLowSurrogate() -> return true
                else -> index++
            }
        }
        return false
    }
}
