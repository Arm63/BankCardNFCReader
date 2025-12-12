package com.emvreader.nfc

/**
 * Result of card reading operation
 */
sealed class CardData {
    /**
     * Successfully read card data
     * 
     * @property pan Raw Primary Account Number (card number)
     * @property formattedPan PAN formatted with spaces (e.g., "4111 1111 1111 1111")
     * @property maskedPan PAN with middle digits masked (e.g., "4111 **** **** 1111")
     * @property cardType Detected card brand
     */
    data class Success(
        val pan: String,
        val formattedPan: String,
        val maskedPan: String,
        val cardType: CardType
    ) : CardData()

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

