package com.emvreader.nfc

/**
 * Human-readable labels for common RID + AID prefixes (hex, uppercase).
 */
object AidLabels {
    /** Short name for display, or **null** if unknown. */
    fun displayName(aidHex: String): String? {
        val a = aidHex.uppercase()
        return when {
            a.startsWith("A0000000031010") ||
                a.startsWith("A0000000032010") -> "Visa Credit/Debit"
            a.startsWith("A000000003") -> "Visa"
            a.startsWith("A0000000041010") ||
                a.startsWith("A0000000043060") ||
                a.startsWith("A0000000043010") -> "Mastercard"
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
     * @property aid Selected payment application identifier (tag `4F`), uppercase hex without spaces.
     */
    data class Success(
        val pan: String,
        val formattedPan: String,
        val maskedPan: String,
        val cardType: CardType,
        val paymentSource: PaymentSource = PaymentSource.UNKNOWN,
        val sourceDetectionResult: CardSourceDetector.DetectionResult? = null,
        val cardholderName: String? = null,
        val aid: String? = null
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
