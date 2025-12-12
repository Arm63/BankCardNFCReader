package com.example.bankcardreader

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bankcardreader.nfc.NfcManager
import com.example.bankcardreader.ui.NfcReaderScreen
import com.example.bankcardreader.ui.NfcReaderUiState
import com.example.bankcardreader.ui.NfcReaderViewModel
import com.example.bankcardreader.ui.theme.BankCardReaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: NfcManager
    private val viewModel: NfcReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        nfcManager = NfcManager.create(this)
        
        // Observe UI state for haptic feedback
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is NfcReaderUiState.Success -> vibrate(VibrationPattern.SUCCESS)
                        is NfcReaderUiState.Error -> vibrate(VibrationPattern.ERROR)
                        else -> { /* no vibration */ }
                    }
                }
            }
        }

        setContent {
            BankCardReaderTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                val nfcStatus by viewModel.nfcStatus.collectAsState()
                val cardNumber by viewModel.cardNumber.collectAsState()

                NfcReaderScreen(
                    uiState = uiState,
                    nfcStatus = nfcStatus,
                    cardNumber = cardNumber,
                    onStartScan = { viewModel.startScanning() },
                    onStopScan = { viewModel.stopScanning() },
                    onOpenNfcSettings = { openNfcSettings() },
                    onCardNumberChange = { viewModel.updateCardNumber(it) },
                    onConfirmCard = { pan -> handleCardConfirmed(pan) },
                    onReset = { viewModel.reset() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update NFC status
        viewModel.updateNfcStatus(nfcManager.getNfcStatus())
        // Enable foreground dispatch when scanning
        if (nfcManager.hasNfc() && nfcManager.isNfcEnabled()) {
            nfcManager.enableForegroundDispatch(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch
        if (nfcManager.hasNfc()) {
            nfcManager.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle NFC intent
        if (nfcManager.isNfcIntent(intent)) {
            nfcManager.getTagFromIntent(intent)?.let { tag ->
                viewModel.processTag(tag)
            }
        }
    }

    private fun openNfcSettings() {
        startActivity(nfcManager.getNfcSettingsIntent())
    }

    private fun handleCardConfirmed(pan: String) {
        // In a real app, this would pass the PAN to the transfer flow
        // For demo, we show a toast and clear the data
        Toast.makeText(
            this,
            "Card ****${pan.takeLast(4)} selected for transfer",
            Toast.LENGTH_SHORT
        ).show()
        
        // Clear sensitive data after use
        viewModel.clearCardData()
    }

    private fun vibrate(pattern: VibrationPattern) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }

        vibrator?.let { v ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = when (pattern) {
                    VibrationPattern.SUCCESS -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    VibrationPattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1)
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (pattern) {
                    VibrationPattern.SUCCESS -> v.vibrate(100)
                    VibrationPattern.ERROR -> v.vibrate(longArrayOf(0, 100, 100, 100), -1)
                }
            }
        }
    }

    private enum class VibrationPattern {
        SUCCESS, ERROR
    }
}
