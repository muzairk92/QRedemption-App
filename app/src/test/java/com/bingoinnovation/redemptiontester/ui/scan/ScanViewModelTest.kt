package com.bingoinnovation.redemptiontester.ui.scan

import com.bingoinnovation.redemptiontester.data.storage.AppSettings
import com.bingoinnovation.redemptiontester.data.storage.CallerRepository
import com.bingoinnovation.redemptiontester.data.storage.HistoryRepository
import com.bingoinnovation.redemptiontester.data.storage.SettingsRepository
import com.bingoinnovation.redemptiontester.data.storage.Vault
import com.bingoinnovation.redemptiontester.testing.InMemoryDataStore
import com.bingoinnovation.redemptiontester.testing.MainDispatcherRule
import com.bingoinnovation.redemptiontester.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = 0L

    private fun TestScope.viewModel(): ScanViewModel {
        val vault = InMemoryDataStore(Vault(caller = TestData.credentials))
        val viewModel = ScanViewModel(
            callers = CallerRepository(vault),
            settings = SettingsRepository(InMemoryDataStore(AppSettings(tabletId = TestData.TABLET_ID))),
            history = HistoryRepository(vault),
            clock = { now },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun showsCallerServerAndTablet() = runTest {
        val state = viewModel().uiState.value

        assertThat(state.callerId).isEqualTo(TestData.CALLER_ID)
        assertThat(state.serverBaseUrl).isEqualTo("https://staging.bingoinnovation.com")
        assertThat(state.tabletId).isEqualTo(TestData.TABLET_ID)
    }

    @Test
    fun ticket_opensOnce_andScanningPausesUntilTheScreenReturns() = runTest {
        val viewModel = viewModel()

        viewModel.onCodeScanned(TestData.TICKET_TEXT)
        assertThat(viewModel.uiState.value.ticketToOpen).isEqualTo(TestData.TICKET_TEXT)

        viewModel.onTicketOpened()
        // Frames still arriving during the screen transition are ignored.
        viewModel.onCodeScanned(TestData.TICKET_TEXT)
        assertThat(viewModel.uiState.value.ticketToOpen).isNull()
        assertThat(viewModel.uiState.value.scanningEnabled).isFalse()

        viewModel.onScreenResumed()
        assertThat(viewModel.uiState.value.scanningEnabled).isTrue()
    }

    @Test
    fun otherCodes_showAMessageOncePerFewSeconds() = runTest {
        val viewModel = viewModel()

        viewModel.onCodeScanned("https://example.com")
        assertThat(viewModel.uiState.value.message).isEqualTo(ScanViewModel.Message.NOT_A_TICKET)
        viewModel.onMessageShown()

        now += 1_000
        viewModel.onCodeScanned("https://example.com")
        assertThat(viewModel.uiState.value.message).isNull()

        now += 3_000
        viewModel.onCodeScanned("https://example.com")
        assertThat(viewModel.uiState.value.message).isEqualTo(ScanViewModel.Message.NOT_A_TICKET)
    }

    @Test
    fun callerLabel_isRecognised() = runTest {
        val viewModel = viewModel()

        viewModel.onCodeScanned("""{"pid":"${TestData.CALLER_ID}","ak":"${TestData.API_KEY}"}""")

        assertThat(viewModel.uiState.value.message).isEqualTo(ScanViewModel.Message.CALLER_LABEL_SCANNED)
    }

    @Test
    fun typedCode_isTrimmedButOtherwiseExact() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.onManualCode("not a ticket")).isFalse()
        assertThat(viewModel.onManualCode("  ${TestData.TICKET_TEXT}\n")).isTrue()
        assertThat(viewModel.uiState.value.ticketToOpen).isEqualTo(TestData.TICKET_TEXT)
    }
}
