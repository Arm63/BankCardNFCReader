package com.emvreader.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Convenient manager for NFC card reading with Activity lifecycle handling.
 * 
 * Usage:
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     private lateinit var nfcManager: NfcCardManager
 *     
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         nfcManager = NfcCardManager(this) { result ->
 *             when (result) {
 *                 is CardData.Success -> showCard(result)
 *                 is CardData.Error -> showError(result.message)
 *             }
 *         }
 *     }
 *     
 *     override fun onResume() {
 *         super.onResume()
 *         nfcManager.enableReading()
 *     }
 *     
 *     override fun onPause() {
 *         super.onPause()
 *         nfcManager.disableReading()
 *     }
 * }
 * ```
 */
class NfcCardManager(
    private val activity: Activity,
    private val config: ReaderConfig = ReaderConfig(),
    private val onCardRead: (CardData) -> Unit
) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val reader = EmvCardReader(config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Check if device has NFC hardware
     */
    val isNfcSupported: Boolean get() = nfcAdapter != null

    /**
     * Check if NFC is enabled in device settings
     */
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    /**
     * Enable NFC reading. Call this in onResume().
     */
    fun enableReading() {
        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
        )
    }

    /**
     * Disable NFC reading. Call this in onPause().
     */
    fun disableReading() {
        nfcAdapter?.disableReaderMode(activity)
    }

    /**
     * Release resources. Call this in onDestroy() if needed.
     */
    fun release() {
        scope.cancel()
    }

    private fun handleTag(tag: Tag) {
        scope.launch {
            val result = reader.readCard(tag)
            onCardRead(result)
        }
    }
}

