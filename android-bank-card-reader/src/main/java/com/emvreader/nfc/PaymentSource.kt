package com.emvreader.nfc

/**
 * Represents the source/origin of a payment card detected via NFC.
 * 
 * ## Detection Methodology
 * 
 * This enum identifies whether a scanned card is a physical plastic card or a tokenized
 * digital wallet card. Detection uses these EMV tags:
 * 
 * ### EMV Tag 9F6E - Form Factor Indicator (FFI)
 * - Indicates the physical form factor of the payment device
 * - Physical cards typically return: 0x30 0x30 (ASCII "00") or 0x01-0x03
 * - Mobile devices typically return: 0x31 0x30 (ASCII "10") or 0x05-0x06
 * 
 * ### EMV Tag 9F19 - Token Requestor ID
 * - Identifies the entity that requested tokenization (wallet provider)
 * - Each wallet provider has a unique ID per card network
 * 
 * ### Token Requestor IDs by Network:
 * 
 * **Mastercard (MDES - Mastercard Digital Enablement Service):**
 * - Apple Pay: 50110030273
 * - Google Wallet: 50120834693
 * - Samsung Pay: 50139059239
 * - Garmin Pay: 50148149593
 * - Fitbit Pay: 50152609117
 * - Microsoft Wallet: 50148183749
 * 
 * **Visa (VTS - Visa Token Service):**
 * - Apple Pay: 40010030273
 * - Google Wallet: 40010075001
 * - Samsung Pay: 40010043095
 * - Garmin Pay: 40010075196
 * - Fitbit Pay: 40010075839
 * 
 * **American Express:**
 * - Apple Pay: Uses Amex-specific token indicators
 * - Google Wallet: Uses Amex-specific token indicators
 * 
 * ### Additional Detection Signals:
 * - **Tokenized PANs (DPANs):** Device-specific PANs that differ from the original card PAN
 * - **AID variations:** Some wallets use specific AID extensions
 * - **Application Label (tag 50):** May contain wallet-specific identifiers
 */
enum class PaymentSource(val displayName: String, val description: String) {
    /**
     * Traditional plastic payment card with physical EMV chip.
     * This is the standard credit/debit card form factor.
     */
    PHYSICAL_CARD("Physical Card", "Traditional plastic card with EMV chip"),
    
    /**
     * Google Wallet (formerly Google Pay / Android Pay).
     * Tokenized card stored in Google's digital wallet on Android devices.
     * Uses Google's Token Requestor IDs for Visa, Mastercard, etc.
     */
    GOOGLE_WALLET("Google Wallet", "Google Wallet / Google Pay tokenized card"),
    
    /**
     * Samsung Pay digital wallet.
     * Tokenized card stored in Samsung's digital wallet on Samsung devices.
     * Uses MST (Magnetic Secure Transmission) and NFC technologies.
     */
    SAMSUNG_PAY("Samsung Pay", "Samsung Pay tokenized card"),
    
    /**
     * Apple Pay digital wallet.
     * Tokenized card stored in Apple's digital wallet on iOS devices.
     * Note: Apple Pay may not be fully detectable on Android due to platform restrictions.
     */
    APPLE_PAY("Apple Pay", "Apple Pay tokenized card"),
    
    /**
     * Garmin Pay for Garmin smartwatches.
     * Tokenized card stored in Garmin's wearable payment solution.
     */
    GARMIN_PAY("Garmin Pay", "Garmin Pay tokenized card"),
    
    /**
     * Fitbit Pay for Fitbit wearables.
     * Tokenized card stored in Fitbit's wearable payment solution.
     */
    FITBIT_PAY("Fitbit Pay", "Fitbit Pay tokenized card"),
    
    /**
     * Generic mobile wallet - we detected a mobile device but couldn't identify
     * the specific wallet provider (could be Google Wallet, Apple Pay, Samsung Pay, etc.)
     * This is returned when Form Factor indicates mobile but no Token Requestor ID is present.
     */
    MOBILE_WALLET("Mobile Wallet", "Mobile device wallet (provider unknown)"),
    
    /**
     * Other digital wallet not specifically identified.
     * Used when we detect some wallet indicators but can't categorize it.
     */
    OTHER_WALLET("Other Wallet", "Unknown digital wallet"),
    
    /**
     * Unable to determine payment source.
     * Card read successfully but form factor could not be identified.
     */
    UNKNOWN("Unknown", "Unable to determine payment source");
    
    /**
     * Whether this payment source represents a digital/mobile wallet.
     * Returns true for all wallet types including generic MOBILE_WALLET.
     */
    val isDigitalWallet: Boolean
        get() = this != PHYSICAL_CARD && this != UNKNOWN
    
    /**
     * Whether the specific wallet provider is known.
     * Returns false for MOBILE_WALLET, OTHER_WALLET, and UNKNOWN.
     */
    val isSpecificWalletKnown: Boolean
        get() = this in setOf(GOOGLE_WALLET, SAMSUNG_PAY, APPLE_PAY, GARMIN_PAY, FITBIT_PAY)
    
    /**
     * Whether this payment source represents a physical card.
     */
    val isPhysicalCard: Boolean
        get() = this == PHYSICAL_CARD
}
