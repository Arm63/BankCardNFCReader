package com.emvreader.nfc

import java.util.Calendar

/**
 * Card application expiration date from EMV tag `5F24`.
 *
 * @property year Four-digit Gregorian year (e.g. 2027).
 * @property month Month, 1..12.
 */
data class ExpiryDate(val year: Int, val month: Int) {
    /** Display as `MM/YY` (e.g. `12/27`). */
    fun displayMmYy(): String = "%02d/%02d".format(month, year % 100)

    /** Display as `MM/YYYY` (e.g. `12/2027`). */
    fun displayMmYyyy(): String = "%02d/%04d".format(month, year)

    /**
     * True if [now] is after the expiry month. EMV cards expire at end of the printed month,
     * so a card with expiry `12/2025` is valid through 2025-12-31.
     */
    fun isExpired(now: Calendar = Calendar.getInstance()): Boolean {
        val nowYear = now.get(Calendar.YEAR)
        val nowMonth = now.get(Calendar.MONTH) + 1
        return nowYear > year || (nowYear == year && nowMonth > month)
    }
}

/**
 * Human-readable labels for common RID + AID prefixes (hex, uppercase).
 */
object AidLabels {
    /** Short name for display, or **null** if unknown. */
    fun displayName(aidHex: String): String? {
        val a = aidHex.uppercase()
        return when {
            a.startsWith("A0000000031010") -> "Visa Credit/Debit"
            a.startsWith("A0000000032010") -> "Visa Electron"
            a.startsWith("A000000003") -> "Visa"
            a.startsWith("A0000000043060") -> "Maestro"
            a.startsWith("A000000004") -> "Mastercard"
            a.startsWith("A000000025") -> "American Express"
            a.startsWith("A0000001523010") -> "Discover"
            a.startsWith("A000000333") -> "UnionPay"
            a.startsWith("A0000000651010") -> "JCB"
            a.startsWith("A0000006581010") -> "Mir"
            else -> null
        }
    }
}

/**
 * Result of card reading operation
 */
sealed class CardData {
    /**
     * Successfully read card data
     * 
     * @property pan Raw Primary Account Number (card number). 
     *               Note: For tokenized wallets, this is the DPAN (Device PAN), not the actual card number.
     * @property formattedPan PAN formatted with spaces (e.g., "4111 1111 1111 1111")
     * @property maskedPan PAN with middle digits masked (e.g., "4111 **** **** 1111")
     * @property cardType Detected card brand (Visa, Mastercard, etc.)
     * @property paymentSource Source of the payment (physical card, Google Wallet, Samsung Pay, etc.)
     * @property sourceDetectionResult Detailed detection result with confidence and debug info
     * @property cardholderName Cardholder name from EMV tag 5F20 when present. Often **null** on contactless for privacy.
     * @property aid Identifier of the application that was actually read (DF name `84` from SELECT,
     *               else the directory `4F`), uppercase hex without spaces.
     * @property pinTriesRemaining Offline PIN tries left (tag `9F17`) when the card exposes it; often **null** on contactless.
     * @property expiryDate Application expiration date from tag `5F24` (`MM/YY`). **null** when card omits it.
     */
    data class Success(
        val pan: String,
        val formattedPan: String,
        val maskedPan: String,
        val cardType: CardType,
        val paymentSource: PaymentSource = PaymentSource.UNKNOWN,
        val sourceDetectionResult: CardSourceDetector.DetectionResult? = null,
        val cardholderName: String? = null,
        val aid: String? = null,
        val pinTriesRemaining: Int? = null,
        val expiryDate: ExpiryDate? = null
    ) : CardData() {

        /**
         * Friendly name for [aid] from [AidLabels], or **null** if unknown.
         */
        val aidDisplayName: String?
            get() = aid?.let { AidLabels.displayName(it) }

        /**
         * Whether the card data came from a digital wallet (Google Wallet, Samsung Pay, etc.)
         */
        val isTokenizedWallet: Boolean
            get() = paymentSource.isDigitalWallet
        
        /**
         * Whether the card is a physical plastic card
         */
        val isPhysicalCard: Boolean
            get() = paymentSource.isPhysicalCard

        /**
         * Masked owner name for display (e.g. `A**** M****`). **null** if [cardholderName] is missing or blank.
         */
        fun maskedOwnerName(): String? {
            val raw = cardholderName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts = raw.split(Regex("[\\s/]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            return parts.joinToString(" ") { token ->
                val c = token.firstOrNull { !it.isWhitespace() } ?: return@joinToString ""
                "${c.uppercaseChar()}****"
            }.trim().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Error reading card
     * 
     * @property code Error type for programmatic handling
     * @property message Human-readable error description
     */
    data class Error(
        val code: ErrorCode,
        val message: String
    ) : CardData()
}

/**
 * Card brand/type
 */
enum class CardType(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    DISCOVER("Discover"),
    UNIONPAY("UnionPay"),
    JCB("JCB"),
    MIR("Mir"),
    UNKNOWN("Unknown");

    companion object {
        fun detect(pan: String): CardType = when {
            pan.startsWith("4") -> VISA
            pan.startsWith("5") && pan.getOrNull(1) in '1'..'5' -> MASTERCARD
            pan.startsWith("2") && pan.length >= 4 && 
                pan.substring(0, 4).toIntOrNull() in 2221..2720 -> MASTERCARD
            pan.startsWith("34") || pan.startsWith("37") -> AMEX
            pan.startsWith("6011") || pan.startsWith("65") ||
                (pan.startsWith("64") && pan.getOrNull(2) in '4'..'9') -> DISCOVER
            pan.startsWith("62") -> UNIONPAY
            pan.startsWith("35") -> JCB
            pan.startsWith("220") -> MIR
            else -> UNKNOWN
        }

        /**
         * Brand from the application's RID (first 5 bytes of the AID), e.g. Maestro → [MASTERCARD].
         * Use when [detect] can't place the PAN. Returns [UNKNOWN] for unrecognised RIDs.
         */
        fun fromAid(aidHex: String?): CardType = when (aidHex?.uppercase()?.take(10)) {
            "A000000003" -> VISA
            "A000000004" -> MASTERCARD
            "A000000025" -> AMEX
            "A000000152" -> DISCOVER
            "A000000333" -> UNIONPAY
            "A000000065" -> JCB
            "A000000658" -> MIR
            else -> UNKNOWN
        }
    }
}

/**
 * Error codes for card reading failures
 */
enum class ErrorCode {
    /** Card doesn't support IsoDep/ISO 14443-4 */
    UNSUPPORTED_CARD,
    
    /** PPSE (2PAY.SYS.DDF01) not found - card may not support contactless */
    PPSE_NOT_FOUND,
    
    /** No supported payment application found on card */
    AID_NOT_FOUND,
    
    /** GET PROCESSING OPTIONS command failed */
    GPO_FAILED,
    
    /** Could not extract PAN from card data */
    PAN_NOT_FOUND,
    
    /** Card was removed during read operation */
    TAG_LOST,
    
    /** NFC communication error */
    COMMUNICATION_ERROR,
    
    /** Unexpected error */
    UNKNOWN
}
