package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.caller.CallerCheck
import com.bingoinnovation.redemptiontester.domain.redemption.ContractRule
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import com.bingoinnovation.redemptiontester.domain.redemption.UnexpectedReason
import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class PortalClientTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var client: PortalClient
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
        client = PortalClient(
            httpClient = PortalHttpClientFactory.create().newBuilder().readTimeout(1, TimeUnit.SECONDS).build(),
            json = json,
            userAgent = "RedemptionTester/test",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun respond(status: Int, body: String, vararg headers: Pair<String, String>) {
        val response = MockResponse().setResponseCode(status).setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        server.enqueue(response)
    }

    @Test
    fun redeem_sendsExactlyTheDocumentedRequest() = runTest {
        respond(200, TestData.successJson(), "Content-Type" to "application/json")

        client.redeem(baseUrl, TestData.request())

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/caller/redemptions")
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json")
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/json")
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body.keys).containsExactly("CallerSN", "APIKey", "qr", "player_tablet_id", "request_id")
        assertThat(body.string("CallerSN")).isEqualTo(TestData.CALLER_ID)
        assertThat(body.string("APIKey")).isEqualTo(TestData.API_KEY)
        assertThat(body.string("qr")).isEqualTo(TestData.TICKET_TEXT)
        assertThat(body.string("player_tablet_id")).isEqualTo(TestData.TABLET_ID)
        assertThat(body.string("request_id")).isEqualTo(TestData.REQUEST_ID)
    }

    @Test
    fun redeem_keepsTabletIdBytesExactly() = runTest {
        val tablet = " Tablet ñ "
        respond(200, TestData.successJson(tabletId = tablet))

        val outcome = client.redeem(baseUrl, TestData.request(tabletId = tablet))

        val sent = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(sent.string("player_tablet_id")).isEqualTo(tablet)
        assertThat((outcome as RedemptionOutcome.Success).checks.first { it.rule == ContractRule.TABLET_ID_ECHOED }.passed)
            .isTrue()
    }

    @Test
    fun redeem_readsTheStagingSuccessAndPassesEveryCheck() = runTest {
        respond(200, TestData.successJson())

        val outcome = client.redeem(baseUrl, TestData.request())

        assertThat(outcome).isInstanceOf(RedemptionOutcome.Success::class.java)
        val success = outcome as RedemptionOutcome.Success
        assertThat(success.redemption).isEqualTo(TestData.redemption())
        assertThat(success.redemption.totalFaces).isEqualTo(46L)
        assertThat(success.checks.map { it.rule })
            .containsExactlyElementsIn(ContractRule.entries - ContractRule.RECOVERY_MATCHES_ORIGINAL)
        assertThat(success.allChecksPassed).isTrue()
    }

    @Test
    fun redeem_flagsContractBreaksWithoutHidingTheResult() = runTest {
        respond(200, TestData.successJson(tabletId = "someone-else", extraTopLevel = ""","debug":true"""))

        val outcome = client.redeem(baseUrl, TestData.request()) as RedemptionOutcome.Success

        val failed = outcome.checks.filterNot { it.passed }.map { it.rule }
        assertThat(failed).containsExactly(ContractRule.TABLET_ID_ECHOED, ContractRule.NO_UNEXPECTED_FIELDS)
    }

    @Test
    fun redeem_successWithMissingField_isUnexpected() = runTest {
        respond(200, TestData.successJson().replace("\"state\": \"redeemed\",", ""))

        val outcome = client.redeem(baseUrl, TestData.request())

        assertThat(outcome).isInstanceOf(RedemptionOutcome.UnexpectedResponse::class.java)
        assertThat((outcome as RedemptionOutcome.UnexpectedResponse).reason)
            .isEqualTo(UnexpectedReason.SUCCESS_SHAPE_INVALID)
    }

    @Test
    fun redeem_readsDocumentedErrors() = runTest {
        respond(409, TestData.errorJson("already_redeemed", "Already redeemed.", retryable = false))

        val outcome = client.redeem(baseUrl, TestData.request())

        assertThat(outcome).isEqualTo(
            RedemptionOutcome.Rejected(
                httpStatus = 409,
                code = "already_redeemed",
                message = "Already redeemed.",
                retryable = false,
                retryAfterSeconds = null,
            ),
        )
        assertThat((outcome as RedemptionOutcome.Rejected).statusMatchesCode).isTrue()
    }

    @Test
    fun redeem_readsRetryAfterOnRateLimit() = runTest {
        respond(
            429,
            TestData.errorJson("rate_limited", "Too many requests.", retryable = true),
            "Retry-After" to "37",
        )

        val outcome = client.redeem(baseUrl, TestData.request()) as RedemptionOutcome.Rejected

        assertThat(outcome.retryable).isTrue()
        assertThat(outcome.retryAfterSeconds).isEqualTo(37L)
    }

    @Test
    fun redeem_htmlErrorPage_isUnexpected_andHidesSecrets() = runTest {
        respond(502, "<html>Bad gateway ${TestData.API_KEY} ${TestData.TICKET_TEXT}</html>")

        val outcome = client.redeem(baseUrl, TestData.request()) as RedemptionOutcome.UnexpectedResponse

        assertThat(outcome.httpStatus).isEqualTo(502)
        assertThat(outcome.reason).isEqualTo(UnexpectedReason.NOT_JSON)
        assertThat(outcome.bodyPreview).contains("Bad gateway")
        assertThat(outcome.bodyPreview).doesNotContain(TestData.API_KEY)
        assertThat(outcome.bodyPreview).doesNotContain(TestData.TICKET_TEXT)
    }

    @Test
    fun redeem_neverFollowsRedirects() = runTest {
        respond(302, "", "Location" to "https://elsewhere.example/api/caller/redemptions")

        val outcome = client.redeem(baseUrl, TestData.request()) as RedemptionOutcome.UnexpectedResponse

        assertThat(outcome.reason).isEqualTo(UnexpectedReason.REDIRECT)
        assertThat(outcome.bodyPreview).contains("elsewhere.example")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun redeem_timeout_isReportedAsOutcomeUnknown() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val outcome = client.redeem(baseUrl, TestData.request())

        assertThat(outcome).isEqualTo(RedemptionOutcome.TransportFailure(TransportFailureKind.TIMEOUT))
        assertThat(TransportFailureKind.TIMEOUT.outcomeUnknown).isTrue()
    }

    @Test
    fun redeem_unreachableServer_isNotSent() = runTest {
        // A port that was just free, so nothing is listening on it.
        val closedPort = ServerSocket(0).use { it.localPort }

        val outcome = client.redeem("http://127.0.0.1:$closedPort", TestData.request())

        assertThat(outcome).isEqualTo(RedemptionOutcome.TransportFailure(TransportFailureKind.CONNECTION_FAILED))
    }

    @Test
    fun redeem_invalidAddress_isNotSent() = runTest {
        val outcome = client.redeem("not a url", TestData.request())

        assertThat(outcome).isEqualTo(RedemptionOutcome.TransportFailure(TransportFailureKind.INVALID_ADDRESS))
    }

    @Test
    fun redeem_keepsAPortalInstalledBelowTheSiteRoot() = runTest {
        respond(200, TestData.successJson())

        client.redeem("$baseUrl/portal", TestData.request())

        assertThat(server.takeRequest().path).isEqualTo("/portal/api/caller/redemptions")
    }

    @Test
    fun verifyCaller_mapsEveryAnswer() = runTest {
        respond(200, """{"valid":true,"message":"Caller ID and API Key are valid."}""")
        respond(401, """{"valid":false,"message":"Invalid Caller ID or API Key."}""")
        respond(422, """{"valid":false,"message":"Invalid Caller ID or API Key."}""")
        respond(404, "<html>Not found</html>")
        respond(429, """{"message":"Too Many Attempts."}""", "Retry-After" to "12")
        respond(500, "oops")

        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.Valid)
        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.Rejected)
        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.Rejected)
        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.NotAvailable)
        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.RateLimited(12))
        assertThat(client.verifyCaller(baseUrl, TestData.credentials)).isEqualTo(CallerCheck.UnexpectedStatus(500))

        val sent = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(sent.keys).containsExactly("CallerID", "APIKey")
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
