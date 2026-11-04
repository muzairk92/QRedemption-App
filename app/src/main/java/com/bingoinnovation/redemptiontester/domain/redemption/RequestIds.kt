package com.bingoinnovation.redemptiontester.domain.redemption

import java.util.UUID

/**
 * Creates `request_id` values: one per scan, reused unchanged for every retry of that
 * scan. The server uses it to tell "the same scan sent again" (recovered) apart from
 * "a new scan of an already used ticket" (already redeemed).
 */
fun interface RequestIdGenerator {
    fun next(): String
}

/** Lowercase UUID version 4, as the Portal and Commerce require. */
object RandomRequestIdGenerator : RequestIdGenerator {
    // java.util.UUID.randomUUID() is a version 4 UUID and toString() is lowercase.
    override fun next(): String = UUID.randomUUID().toString()
}

object RequestIdRules {
    private val UUID_V4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    /** True for a lowercase UUID v4 with the RFC variant, the format the server accepts. */
    fun isValid(value: String): Boolean = UUID_V4.matches(value)
}
