package com.bingoinnovation.redemptiontester.data.network

import com.bingoinnovation.redemptiontester.domain.redemption.TransportFailureKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class FailureKindTest {

    @Test
    fun failuresBeforeSending_areReportedAsNotSent() {
        val notSent = mapOf(
            UnknownHostException("staging.bingoinnovation.com") to TransportFailureKind.HOST_NOT_FOUND,
            ConnectException("Connection refused") to TransportFailureKind.CONNECTION_FAILED,
            SSLHandshakeException("Certificate not trusted") to TransportFailureKind.TLS_FAILED,
            SSLPeerUnverifiedException("Hostname not verified") to TransportFailureKind.TLS_FAILED,
            UnknownServiceException("CLEARTEXT communication not permitted") to TransportFailureKind.CLEARTEXT_BLOCKED,
        )
        notSent.forEach { (exception, kind) ->
            assertThat(exception.toFailureKind()).isEqualTo(kind)
            assertThat(kind.outcomeUnknown).isFalse()
        }
    }

    @Test
    fun failuresThatMayFollowSending_areReportedAsOutcomeUnknown() {
        val unknown = mapOf(
            SocketTimeoutException("timeout") to TransportFailureKind.TIMEOUT,
            InterruptedIOException("timeout") to TransportFailureKind.TIMEOUT,
            // How Android reports a connection reset while the answer is being read.
            SSLException("Read error: I/O error during system call, Connection reset by peer")
                to TransportFailureKind.NETWORK_ERROR,
            IOException("unexpected end of stream") to TransportFailureKind.NETWORK_ERROR,
        )
        unknown.forEach { (exception, kind) ->
            assertThat(exception.toFailureKind()).isEqualTo(kind)
            assertThat(kind.outcomeUnknown).isTrue()
        }
    }
}
