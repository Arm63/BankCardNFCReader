package com.emvreader.bankcardreader.nfc

/**
 * Possible card reading errors
 */
enum class CardReadError(val userMessage: String) {
    UNSUPPORTED_CARD("This card type is not supported"),
    PPSE_NOT_FOUND("Card does not support contactless reading"),
    AID_NOT_FOUND("Payment application not found on card"),
    GPO_FAILED("Failed to initialize card communication"),
    PAN_NOT_FOUND("Could not read card number"),
    TAG_LOST("Card was removed too quickly. Please hold steady."),
    COMMUNICATION_ERROR("Communication error. Please try again."),
    UNKNOWN_ERROR("An unexpected error occurred")
}
