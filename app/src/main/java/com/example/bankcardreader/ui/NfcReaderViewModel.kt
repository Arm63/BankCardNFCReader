package com.example.bankcardreader.ui

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bankcardreader.nfc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for NFC card reading screen.
 * Manages UI state and card reading operations.
 */
class NfcReaderViewModel : ViewModel() {

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
     * Process detected NFC tag
     */
    fun processTag(tag: Tag) {
        if (_uiState.value !is NfcReaderUiState.Scanning) return

        _uiState.value = NfcReaderUiState.Reading

        viewModelScope.launch {
            when (val result = cardReader.readCard(tag)) {
                is CardReadResult.Success -> {
                    _cardNumber.value = result.pan
                    _uiState.value = NfcReaderUiState.Success(
                        pan = result.pan,
                        formattedPan = result.formattedPan,
                        cardType = result.cardType
                    )
                }
                is CardReadResult.Error -> {
                    _uiState.value = NfcReaderUiState.Error(
                        error = result.error,
                        message = result.message ?: result.error.userMessage
                    )
                }
            }
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
    
    data class Success(
        val pan: String,
        val formattedPan: String,
        val cardType: CardValidator.CardType
    ) : NfcReaderUiState()
    
    data class Error(
        val error: CardReadError,
        val message: String
    ) : NfcReaderUiState()
}

