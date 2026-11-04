package com.bingoinnovation.redemptiontester.testing

import androidx.datastore.core.DataStore
import com.bingoinnovation.redemptiontester.data.network.PortalGateway
import com.bingoinnovation.redemptiontester.data.security.DataCipher
import com.bingoinnovation.redemptiontester.domain.caller.CallerCheck
import com.bingoinnovation.redemptiontester.domain.caller.CallerCredentials
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionOutcome
import com.bingoinnovation.redemptiontester.domain.redemption.RedemptionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.GeneralSecurityException

/** A deterministic, in-memory [DataStore] for repository and ViewModel tests. */
class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}

/** Answers redemptions from a queue and records what was sent. */
class FakePortalGateway : PortalGateway {
    val redeemRequests = mutableListOf<Pair<String, RedemptionRequest>>()
    private val outcomes = ArrayDeque<RedemptionOutcome>()
    var callerCheck: CallerCheck = CallerCheck.Valid

    /** Runs during [redeem], before the answer is returned. */
    var onRedeem: suspend () -> Unit = {}

    fun enqueue(vararg outcome: RedemptionOutcome) {
        outcomes.addAll(outcome)
    }

    override suspend fun redeem(baseUrl: String, request: RedemptionRequest): RedemptionOutcome {
        redeemRequests += baseUrl to request
        onRedeem()
        return outcomes.removeFirst()
    }

    override suspend fun verifyCaller(baseUrl: String, credentials: CallerCredentials): CallerCheck = callerCheck
}

/** Reversible and recognisable, so tests can prove data is not stored as plain text. */
class XorDataCipher : DataCipher {
    override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(MARKER) + plain.map { (it.toInt() xor KEY).toByte() }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.isEmpty() || encrypted[0] != MARKER) throw GeneralSecurityException("Not encrypted by this cipher.")
        return encrypted.drop(1).map { (it.toInt() xor KEY).toByte() }.toByteArray()
    }

    private companion object {
        const val MARKER: Byte = 0x7F
        const val KEY = 0x5A
    }
}
