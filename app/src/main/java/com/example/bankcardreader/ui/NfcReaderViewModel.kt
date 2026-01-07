package com.example.bankcardreader.ui

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bankcardreader.nfc.*
// Import library classes for payment source detection
import com.emvreader.nfc.CardSourceDetector
import com.emvreader.nfc.EmvCardReader
import com.emvreader.nfc.CardData
import com.emvreader.nfc.PaymentSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for NFC card reading screen.
 * Manages UI state and card reading operations.
 * 
 * Uses the library's EmvCardReader for enhanced card reading with payment source detection.
 */
class NfcReaderViewModel : ViewModel() {

    // Use library's EmvCardReader for enhanced detection
    private val libraryReader = EmvCardReader()
    
    // Keep existing reader for fallback
    private val cardReader = NfcCardReader()

    private val _uiState = MutableStateFlow<NfcReaderUiState>(NfcReaderUiState.Idle)
    val uiState: StateFlow<NfcReaderUiState> = _uiState.asStateFlow()

    private val _nfcStatus = MutableStateFlow(NfcStatus.NOT_AVAILABLE)
    val nfcStatus: StateFlow<NfcStatus> = _nfcStatus.asStateFlow()

    private val _cardNumber = MutableStateFlow("")
    val cardNumber: StateFlow<String> = _cardNumber.asStateFlow()

    /**
     * Update NFC status from NfcManager
     */
    fun updateNfcStatus(status: NfcStatus) {
        _nfcStatus.value = status
        if (status == NfcStatus.DISABLED && _uiState.value is NfcReaderUiState.Scanning) {
            _uiState.value = NfcReaderUiState.NfcDisabled
        }
    }

    /**
     * Start scanning for NFC cards
     */
    fun startScanning() {
        when (_nfcStatus.value) {
            NfcStatus.NOT_AVAILABLE -> {
                _uiState.value = NfcReaderUiState.NfcNotAvailable
            }
            NfcStatus.DISABLED -> {
                _uiState.value = NfcReaderUiState.NfcDisabled
            }
            NfcStatus.ENABLED -> {
                _uiState.value = NfcReaderUiState.Scanning
            }
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        _uiState.value = NfcReaderUiState.Idle
    }

    /**
     * Process detected NFC tag using the library's EmvCardReader
     * which includes payment source detection (physical card vs digital wallet)
     */
    fun processTag(tag: Tag) {
        if (_uiState.value !is NfcReaderUiState.Scanning) return

        _uiState.value = NfcReaderUiState.Reading

        viewModelScope.launch {
            // Use library's reader for payment source detection
            when (val result = libraryReader.readCard(tag)) {
                is CardData.Success -> {
                    _cardNumber.value = result.pan
                    _uiState.value = NfcReaderUiState.Success(
                        pan = result.pan,
                        formattedPan = result.formattedPan,
                        cardType = mapCardType(result.cardType),
                        paymentSource = result.paymentSource,
                        isTokenizedWallet = result.isTokenizedWallet,
                        detectionConfidence = result.sourceDetectionResult?.confidence
                    )
                }
                is CardData.Error -> {
                    _uiState.value = NfcReaderUiState.Error(
                        error = mapErrorCode(result.code),
                        message = result.message
                    )
                }
            }
        }
    }
    
    /**
     * Map library's CardType to app's CardValidator.CardType
     */
    private fun mapCardType(libraryType: com.emvreader.nfc.CardType): CardValidator.CardType {
        return when (libraryType) {
            com.emvreader.nfc.CardType.VISA -> CardValidator.CardType.VISA
            com.emvreader.nfc.CardType.MASTERCARD -> CardValidator.CardType.MASTERCARD
            com.emvreader.nfc.CardType.AMEX -> CardValidator.CardType.AMEX
            com.emvreader.nfc.CardType.DISCOVER -> CardValidator.CardType.DISCOVER
            com.emvreader.nfc.CardType.JCB -> CardValidator.CardType.JCB
            com.emvreader.nfc.CardType.UNIONPAY -> CardValidator.CardType.UNIONPAY
            com.emvreader.nfc.CardType.MIR -> CardValidator.CardType.MIR
            com.emvreader.nfc.CardType.UNKNOWN -> CardValidator.CardType.UNKNOWN
        }
    }
    
    /**
     * Map library's ErrorCode to app's CardReadError
     */
    private fun mapErrorCode(libraryCode: com.emvreader.nfc.ErrorCode): CardReadError {
        return when (libraryCode) {
            com.emvreader.nfc.ErrorCode.UNSUPPORTED_CARD -> CardReadError.UNSUPPORTED_CARD
            com.emvreader.nfc.ErrorCode.PPSE_NOT_FOUND -> CardReadError.PPSE_NOT_FOUND
            com.emvreader.nfc.ErrorCode.AID_NOT_FOUND -> CardReadError.AID_NOT_FOUND
            com.emvreader.nfc.ErrorCode.GPO_FAILED -> CardReadError.GPO_FAILED
            com.emvreader.nfc.ErrorCode.PAN_NOT_FOUND -> CardReadError.PAN_NOT_FOUND
            com.emvreader.nfc.ErrorCode.TAG_LOST -> CardReadError.TAG_LOST
            com.emvreader.nfc.ErrorCode.COMMUNICATION_ERROR -> CardReadError.COMMUNICATION_ERROR
            com.emvreader.nfc.ErrorCode.UNKNOWN -> CardReadError.UNKNOWN_ERROR
        }
    }

    /**
     * Reset to initial state
     */
    fun reset() {
        _uiState.value = NfcReaderUiState.Idle
        _cardNumber.value = ""
    }

    /**
     * Update manual card number input
     */
    fun updateCardNumber(number: String) {
        _cardNumber.value = CardValidator.cleanCardNumber(number)
    }

    /**
     * Validate current card number
     */
    fun validateCardNumber(): Boolean {
        return CardValidator.isValidCardNumber(_cardNumber.value)
    }

    /**
     * Get formatted card number for display
     */
    fun getFormattedCardNumber(): String {
        return CardValidator.formatCardNumber(_cardNumber.value)
    }

    /**
     * Get masked card number for display
     */
    fun getMaskedCardNumber(): String {
        return CardValidator.maskCardNumber(_cardNumber.value)
    }

    /**
     * Get detected card type
     */
    fun getCardType(): CardValidator.CardType {
        return CardValidator.detectCardType(_cardNumber.value)
    }

    /**
     * Clear card data from memory
     */
    fun clearCardData() {
        _cardNumber.value = ""
        if (_uiState.value is NfcReaderUiState.Success) {
            _uiState.value = NfcReaderUiState.Idle
        }
    }
}

/**
 * UI State for NFC Reader screen
 */
sealed class NfcReaderUiState {
    data object Idle : NfcReaderUiState()
    data object Scanning : NfcReaderUiState()
    data object Reading : NfcReaderUiState()
    data object NfcDisabled : NfcReaderUiState()
    data object NfcNotAvailable : NfcReaderUiState()
    
    /**
     * Successfully read card data with payment source detection.
     * 
     * @property pan Raw Primary Account Number (DPAN for tokenized wallets)
     * @property formattedPan PAN formatted with spaces
     * @property cardType Detected card brand (Visa, Mastercard, etc.)
     * @property paymentSource Source of the card (Physical, Google Wallet, Samsung Pay, etc.)
     * @property isTokenizedWallet True if card is from a digital wallet
     * @property detectionConfidence Confidence level of payment source detection
     */
    data class Success(
        val pan: String,
        val formattedPan: String,
        val cardType: CardValidator.CardType,
        val paymentSource: PaymentSource = PaymentSource.UNKNOWN,
        val isTokenizedWallet: Boolean = false,
        val detectionConfidence: CardSourceDetector.DetectionConfidence? = null
    ) : NfcReaderUiState()
    
    data class Error(
        val error: CardReadError,
        val message: String
    ) : NfcReaderUiState()
}

