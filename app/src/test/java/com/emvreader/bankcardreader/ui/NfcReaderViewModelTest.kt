package com.emvreader.bankcardreader.ui

import com.emvreader.bankcardreader.nfc.CardReadError
import com.emvreader.bankcardreader.nfc.CardValidator
import com.emvreader.bankcardreader.nfc.NfcStatus
import com.emvreader.nfc.CardData
import com.emvreader.nfc.CardType
import com.emvreader.nfc.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcReaderViewModelTest {

    private val success = CardData.Success(
        pan = "4111111111111111",
        formattedPan = "4111 1111 1111 1111",
        maskedPan = "4111 **** **** 1111",
        cardType = CardType.VISA
    )
    private val failure = CardData.Error(ErrorCode.TAG_LOST, "Card was removed too quickly.")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun scanningViewModel() = NfcReaderViewModel().apply {
        updateNfcStatus(NfcStatus.ENABLED)
        startScanning()
    }

    // --- Haptic feedback events ---

    @Test
    fun successfulRead_emitsSuccessHaptic() = runTest {
        val vm = scanningViewModel()
        vm.processRead { success }
        assertEquals(HapticEvent.SUCCESS, vm.hapticEvents.first())
    }

    @Test
    fun failedRead_emitsErrorHaptic() = runTest {
        val vm = scanningViewModel()
        vm.processRead { failure }
        assertEquals(HapticEvent.ERROR, vm.hapticEvents.first())
    }

    @Test
    fun haptic_isNotReplayedToNewCollector() = runTest {
        val vm = scanningViewModel()
        vm.processRead { success }
        vm.hapticEvents.first() // screen before rotation consumes it

        val replayed = withTimeoutOrNull(1_000) { vm.hapticEvents.first() } // screen after rotation

        assertNull(replayed)
    }

    @Test
    fun arcaRead_showsArcaBrand() {
        val vm = scanningViewModel()
        vm.processRead { success.copy(pan = "9051000000000009", cardType = CardType.ARCA) }
        val state = vm.uiState.value as NfcReaderUiState.Success
        assertEquals(CardValidator.CardType.ARCA, state.cardType)
    }

    @Test
    fun read_isIgnoredWhenNotScanning() {
        val vm = NfcReaderViewModel()
        vm.processRead { success }
        assertEquals(NfcReaderUiState.Idle, vm.uiState.value)
    }

    // --- Screens showing card data must be FLAG_SECURE ---

    @Test
    fun successState_showsCardData() {
        val state = NfcReaderUiState.Success("4111111111111111", "4111 1111 1111 1111", CardValidator.CardType.VISA)
        assertTrue(state.showsCardData(cardNumber = ""))
    }

    @Test
    fun manualEntryWithDigits_showsCardData() {
        assertTrue(NfcReaderUiState.Idle.showsCardData(cardNumber = "4111"))
    }

    @Test
    fun idleWithoutCardNumber_doesNotShowCardData() {
        assertFalse(NfcReaderUiState.Idle.showsCardData(cardNumber = ""))
        assertFalse(NfcReaderUiState.Scanning.showsCardData(cardNumber = ""))
        assertFalse(NfcReaderUiState.Error(CardReadError.TAG_LOST, "x").showsCardData(cardNumber = ""))
    }
}
