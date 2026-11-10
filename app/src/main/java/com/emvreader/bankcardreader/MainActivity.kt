package com.emvreader.bankcardreader

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.emvreader.bankcardreader.nfc.NfcManager
import com.emvreader.bankcardreader.ui.HapticEvent
import com.emvreader.bankcardreader.ui.NfcReaderScreen
import com.emvreader.bankcardreader.ui.NfcReaderViewModel
import com.emvreader.bankcardreader.ui.showsCardData
import com.emvreader.bankcardreader.ui.theme.BankCardReaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: NfcManager
    private val viewModel: NfcReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        nfcManager = NfcManager.create(this)
        
        // One-shot haptic feedback per finished read
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hapticEvents.collect { event ->
                    when (event) {
                        HapticEvent.SUCCESS -> vibrate(VibrationPattern.SUCCESS)
                        HapticEvent.ERROR -> vibrate(VibrationPattern.ERROR)
                    }
                }
            }
        }

        setContent {
            BankCardReaderTheme(dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                val nfcStatus by viewModel.nfcStatus.collectAsState()
                val cardNumber by viewModel.cardNumber.collectAsState()

                val showsCardData = uiState.showsCardData(cardNumber)
                LaunchedEffect(showsCardData) { setScreenSecure(showsCardData) }

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
        // Reader mode while in the foreground: cards go straight to us (no NDEF check, no
        // other app grabbing the tag). The callback runs on a binder thread.
        if (nfcManager.isNfcEnabled()) {
            nfcManager.enableReaderMode(this) { tag -> viewModel.processTag(tag) }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Card tapped while the app was in the background (manifest TECH_DISCOVERED filter)
        if (nfcManager.isNfcIntent(intent)) {
            nfcManager.getTagFromIntent(intent)?.let { tag ->
                viewModel.processTag(tag)
            }
        }
    }

    /** Keeps card data out of screenshots, screen recordings and the Recents thumbnail. */
    private fun setScreenSecure(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
