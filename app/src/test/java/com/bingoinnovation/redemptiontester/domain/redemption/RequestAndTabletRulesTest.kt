package com.bingoinnovation.redemptiontester.domain.redemption

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class RequestAndTabletRulesTest {

    @Test
    fun generatedRequestIds_areLowercaseUuidV4_andUnique() {
        val ids = List(500) { RandomRequestIdGenerator.next() }

        ids.forEach { assertThat(RequestIdRules.isValid(it)).isTrue() }
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    @Test
    fun requestIdRules_matchTheServer() {
        assertThat(RequestIdRules.isValid("8dd0aac6-65fb-4ecf-9a49-786951473221")).isTrue()
        assertThat(RequestIdRules.isValid("8DD0AAC6-65FB-4ECF-9A49-786951473221")).isFalse() // uppercase
        assertThat(RequestIdRules.isValid("8dd0aac6-65fb-1ecf-9a49-786951473221")).isFalse() // version 1
        assertThat(RequestIdRules.isValid("8dd0aac6-65fb-4ecf-7a49-786951473221")).isFalse() // wrong variant
        assertThat(RequestIdRules.isValid("8dd0aac665fb4ecf9a49786951473221")).isFalse()
    }

    @Test
    fun tabletId_keepsCaseAndSpaces() {
        assertThat(TabletIdRules.validate("Tablet A ")).isEqualTo(TabletIdRules.Validation.Valid)
        assertThat(TabletIdRules.validate(" ")).isEqualTo(TabletIdRules.Validation.Valid)
        assertThat(TabletIdRules.validate("Mesa ñ 7 ✓")).isEqualTo(TabletIdRules.Validation.Valid)
    }

    @Test
    fun tabletId_limitIsCountedInUtf8Bytes() {
        assertThat(TabletIdRules.validate("a".repeat(128))).isEqualTo(TabletIdRules.Validation.Valid)
        assertThat(TabletIdRules.validate("a".repeat(129))).isEqualTo(TabletIdRules.Validation.TooLong(129))
        // "é" is two bytes in UTF-8: 64 of them is 128 bytes, 65 is 130.
        assertThat(TabletIdRules.validate("é".repeat(64))).isEqualTo(TabletIdRules.Validation.Valid)
        assertThat(TabletIdRules.validate("é".repeat(65))).isEqualTo(TabletIdRules.Validation.TooLong(130))
    }

    @Test
    fun tabletId_rejectsEmptyControlAndBrokenText() {
        assertThat(TabletIdRules.validate("")).isEqualTo(TabletIdRules.Validation.Empty)
        assertThat(TabletIdRules.validate("tab\nlet")).isEqualTo(TabletIdRules.Validation.ControlCharacter)
        assertThat(TabletIdRules.validate("tab\tlet")).isEqualTo(TabletIdRules.Validation.ControlCharacter)
        assertThat(TabletIdRules.validate("tab\u007Flet")).isEqualTo(TabletIdRules.Validation.ControlCharacter)
        assertThat(TabletIdRules.validate("bad\uD800")).isEqualTo(TabletIdRules.Validation.InvalidText)
        assertThat(TabletIdRules.validate("\uDC00bad")).isEqualTo(TabletIdRules.Validation.InvalidText)
        assertThat(TabletIdRules.validate("ok😀")).isEqualTo(TabletIdRules.Validation.Valid)
    }

    @Test
    fun generatedTabletIds_areValidAndReadable() {
        val id = TabletIdRules.generate(Random(42))

        assertThat(id).matches("tester-[a-z0-9]{6}")
        assertThat(TabletIdRules.isValid(id)).isTrue()
    }
}
