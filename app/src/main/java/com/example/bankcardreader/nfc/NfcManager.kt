package com.example.bankcardreader.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.provider.Settings

/**
 * Manager for NFC adapter operations.
 * Handles NFC availability checking and foreground dispatch.
 */
class NfcManager(private val context: Context) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    /**
     * Check if device has NFC hardware
     */
    fun hasNfc(): Boolean = nfcAdapter != null

    /**
     * Check if NFC is enabled
     */
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    /**
     * Get NFC status
     */
    fun getNfcStatus(): NfcStatus {
        return when {
            nfcAdapter == null -> NfcStatus.NOT_AVAILABLE
            !nfcAdapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.ENABLED
        }
    }

    /**
     * Enable foreground dispatch for NFC tag detection
     * Must be called from Activity.onResume()
     */
    fun enableForegroundDispatch(activity: Activity) {
        nfcAdapter?.let { adapter ->
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(
                activity, 0, intent, pendingIntentFlags
            )

            // Filter for IsoDep (ISO 14443-4) tags - used by contactless payment cards
            val techListsArray = arrayOf(
                arrayOf(IsoDep::class.java.name)
            )

            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            )

            adapter.enableForegroundDispatch(
                activity, 
                pendingIntent, 
                filters, 
                techListsArray
            )
        }
    }

    /**
     * Disable foreground dispatch
     * Must be called from Activity.onPause()
     */
    fun disableForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Extract NFC Tag from intent
     */
    fun getTagFromIntent(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    /**
     * Check if intent contains NFC tag
     */
    fun isNfcIntent(intent: Intent): Boolean {
        return intent.action in listOf(
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )
    }

    /**
     * Get intent to open NFC settings
     */
    fun getNfcSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NFC_SETTINGS)
    }

    companion object {
        /**
         * Create NfcManager instance
         */
        fun create(context: Context): NfcManager = NfcManager(context)
    }
}

/**
 * NFC hardware/software status
 */
enum class NfcStatus {
    ENABLED,
    DISABLED,
    NOT_AVAILABLE
}

