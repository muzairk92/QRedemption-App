package com.bingoinnovation.redemptiontester.ui.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bingoinnovation.redemptiontester.R
import com.bingoinnovation.redemptiontester.data.PortalRepository
import com.bingoinnovation.redemptiontester.domain.caller.CallerCheck
import com.bingoinnovation.redemptiontester.ui.components.StatusBanner
import com.bingoinnovation.redemptiontester.ui.components.StatusTone
import com.bingoinnovation.redemptiontester.ui.text.titleRes

/** State of the optional "Check with server" step for Caller credentials. */
sealed interface Verification {
    data object Idle : Verification

    data object Checking : Verification

    data object NoServer : Verification

    data object StorageFailed : Verification

    data class Done(val check: CallerCheck) : Verification
}

fun PortalRepository.VerifyResult.toVerification(): Verification = when (this) {
    is PortalRepository.VerifyResult.Completed -> Verification.Done(check)
    PortalRepository.VerifyResult.NoServer -> Verification.NoServer
}

/** Shows the result of a Caller credential check; nothing while idle or checking. */
@Composable
fun VerificationResult(verification: Verification, modifier: Modifier = Modifier) {
    when (verification) {
        Verification.Idle, Verification.Checking -> Unit
        Verification.NoServer -> StatusBanner(
            tone = StatusTone.WARNING,
            icon = R.drawable.ic_dns,
            title = stringResource(R.string.verify_no_server),
            modifier = modifier,
        )
        Verification.StorageFailed -> StatusBanner(
            tone = StatusTone.ERROR,
            icon = R.drawable.ic_error,
            title = stringResource(R.string.error_storage),
            modifier = modifier,
        )
        is Verification.Done -> when (val check = verification.check) {
            CallerCheck.Valid -> StatusBanner(
                tone = StatusTone.SUCCESS,
                icon = R.drawable.ic_verified,
                title = stringResource(R.string.verify_valid_title),
                body = stringResource(R.string.verify_valid_body),
                modifier = modifier,
            )
            CallerCheck.Rejected -> StatusBanner(
                tone = StatusTone.ERROR,
                icon = R.drawable.ic_error,
                title = stringResource(R.string.verify_rejected_title),
                body = stringResource(R.string.verify_rejected_body),
                modifier = modifier,
            )
            CallerCheck.NotAvailable -> StatusBanner(
                tone = StatusTone.INFO,
                icon = R.drawable.ic_info,
                title = stringResource(R.string.verify_not_available_title),
                body = stringResource(R.string.verify_not_available_body),
                modifier = modifier,
            )
            is CallerCheck.RateLimited -> StatusBanner(
                tone = StatusTone.WARNING,
                icon = R.drawable.ic_schedule,
                title = stringResource(R.string.verify_rate_limited_title),
                body = check.retryAfterSeconds?.let { stringResource(R.string.retry_after_seconds, it) },
                modifier = modifier,
            )
            is CallerCheck.UnexpectedStatus -> StatusBanner(
                tone = StatusTone.WARNING,
                icon = R.drawable.ic_warning,
                title = stringResource(R.string.verify_unexpected_title, check.httpStatus),
                modifier = modifier,
            )
            is CallerCheck.Failed -> StatusBanner(
                tone = StatusTone.WARNING,
                icon = R.drawable.ic_warning,
                title = stringResource(check.kind.titleRes()),
                modifier = modifier,
            )
        }
    }
}

/** Label for the "Check with server" button, including progress. */
@Composable
fun VerifyButtonLabel(verification: Verification) {
    Text(
        stringResource(
            if (verification == Verification.Checking) R.string.verify_checking else R.string.verify_action,
        ),
    )
}
