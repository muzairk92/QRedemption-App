package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class RedemptionContractValidatorTest {

    private fun failedRules(body: String): List<ContractRule> =
        RedemptionContractValidator.check(Json.parseToJsonElement(body).jsonObject, TestData.request())
            .filterNot { it.passed }
            .map { it.rule }

    @Test
    fun stagingAnswer_passes() {
        assertThat(failedRules(TestData.successJson())).isEmpty()
    }

    @Test
    fun sessionWithoutSavedTime_passesWhenAllTimingIsNull() {
        val body = TestData.successJson()
            .replace("\"start_time_local\": \"17:00:00\"", "\"start_time_local\": null")
            .replace("\"timezone_name\": \"America/New_York\"", "\"timezone_name\": null")
            .replace("\"starts_at_utc\": \"2026-11-09T22:00:00Z\"", "\"starts_at_utc\": null")

        assertThat(failedRules(body)).isEmpty()
    }

    @Test
    fun partialTiming_failsSessionCheck() {
        val body = TestData.successJson().replace("\"timezone_name\": \"America/New_York\"", "\"timezone_name\": null")

        assertThat(failedRules(body)).containsExactly(ContractRule.SESSION_VALID)
    }

    @Test
    fun invalidValues_failTheirOwnCheck() {
        assertThat(failedRules(TestData.successJson().replace("\"api_version\": 1", "\"api_version\": \"1\"")))
            .containsExactly(ContractRule.API_VERSION)
        assertThat(failedRules(TestData.successJson(requestId = "11111111-1111-4111-8111-111111111111")))
            .containsExactly(ContractRule.REQUEST_ID_ECHOED)
        assertThat(failedRules(TestData.successJson(redemptionId = "7F8F8C92-1916-4EE0-9455-2473AE9B46E7")))
            .containsExactly(ContractRule.REDEMPTION_ID_FORMAT)
        assertThat(failedRules(TestData.successJson().replace("\"date\": \"2026-11-09\"", "\"date\": \"2026-02-30\"")))
            .containsExactly(ContractRule.SESSION_VALID)
        assertThat(
            failedRules(TestData.successJson().replace("\"America/New_York\"", "\"Mars/Olympus\"")),
        ).containsExactly(ContractRule.SESSION_VALID)
        assertThat(failedRules(TestData.successJson().replace("\"commerce_package_id\": 1", "\"commerce_package_id\": 0")))
            .containsExactly(ContractRule.PACKAGE_VALID)
        assertThat(failedRules(TestData.successJson().replace("\"faces\": 4", "\"faces\": 0")))
            .containsExactly(ContractRule.GAME_TYPES_VALID)
        assertThat(failedRules(TestData.successJson().replace("\"portal_game_type_id\": 14", "\"portal_game_type_id\": 10")))
            .containsExactly(ContractRule.GAME_TYPES_VALID)
    }

    @Test
    fun emptyGameTypeList_fails() {
        val body = TestData.successJson().replace(Regex("\\[[^\\]]*]"), "[]")

        assertThat(failedRules(body)).containsExactly(ContractRule.GAME_TYPES_VALID)
    }

    @Test
    fun extraNestedField_fails() {
        val body = TestData.successJson().replace("\"name\": \"Package 1\"", "\"name\": \"Package 1\", \"price\": 5")

        assertThat(failedRules(body)).containsExactly(ContractRule.NO_UNEXPECTED_FIELDS)
    }
}
