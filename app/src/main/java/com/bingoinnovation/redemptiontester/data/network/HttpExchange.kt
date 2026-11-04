package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

internal sealed interface HttpResult {
    /** [body] is null when the response was larger than the allowed size. */
    data class Completed(
        val status: Int,
        val body: String?,
        val retryAfterSeconds: Long?,
        val location: String?,
    ) : HttpResult

    data class Failed(val kind: TransportFailureKind) : HttpResult
}

/** The HTTP client for Portal requests. */
object PortalHttpClientFactory {

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // The Portal waits up to 35 seconds for BingoCommerce (5 s connect + 30 s total).
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        // Never follow redirects: they could carry the Caller key to another address.
        .followRedirects(false)
        .followSslRedirects(false)
        // Never resend silently. A retry must be a deliberate, recorded attempt.
        .retryOnConnectionFailure(false)
        .build()
}

/**
 * Sends [request] and reads at most [maxBodyBytes] of the answer. Never throws for
 * network problems: they come back as [HttpResult.Failed]. Cancelling the coroutine
 * cancels the HTTP call.
 */
internal suspend fun OkHttpClient.exchange(request: Request, maxBodyBytes: Long): HttpResult =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(HttpResult.Failed(e.toFailureKind()))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { it.readCompleted(maxBodyBytes) }
                    } catch (e: IOException) {
                        HttpResult.Failed(e.toFailureKind())
                    }
                    continuation.resume(result)
                }
            },
        )
    }

private fun Response.readCompleted(maxBodyBytes: Long): HttpResult.Completed {
    val source = body?.source()
    val text = when {
        source == null -> ""
        // request(n) is true when at least n bytes are available: the body is too large.
        source.request(maxBodyBytes + 1) -> null
        else -> source.readUtf8()
    }
    return HttpResult.Completed(
        status = code,
        body = text,
        retryAfterSeconds = header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
        location = header("Location"),
    )
}

internal fun IOException.toFailureKind(): TransportFailureKind = when (this) {
    is UnknownHostException -> TransportFailureKind.HOST_NOT_FOUND
    is SocketTimeoutException -> TransportFailureKind.TIMEOUT
    // OkHttp reports its call timeout as an InterruptedIOException.
    is InterruptedIOException -> TransportFailureKind.TIMEOUT
    is ConnectException -> TransportFailureKind.CONNECTION_FAILED
    is SSLException -> TransportFailureKind.TLS_FAILED
    // OkHttp throws this when the network security config forbids plain HTTP.
    is UnknownServiceException -> TransportFailureKind.CLEARTEXT_BLOCKED
    else -> TransportFailureKind.NETWORK_ERROR
}
