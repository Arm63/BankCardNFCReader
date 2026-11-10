package com.emvreader.bankcardreader.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.provider.Settings

/**
 * Manager for NFC adapter operations.
 * Handles NFC availability checking and reader mode.
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
     * Deliver NFC-A / NFC-B tags to [onTag] while [activity] is resumed.
     * Must be called from Activity.onResume().
     *
     * Reader mode (rather than foreground dispatch) skips the platform NDEF check, which can
     * disturb phone-wallet (HCE) sessions, and the platform sound — the app gives its own
     * haptic feedback when the read finishes. [onTag] runs on a binder thread.
     */
    fun enableReaderMode(activity: Activity, onTag: (Tag) -> Unit) {
        nfcAdapter?.enableReaderMode(
            activity,
            { tag -> onTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    /**
     * Stop reader mode.
     * Must be called from Activity.onPause().
     */
    fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
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

