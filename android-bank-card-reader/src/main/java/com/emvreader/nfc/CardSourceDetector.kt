package com.emvreader.nfc

/**
 * Detects the source/origin of a payment card from EMV data.
 * 
 * ## Technical Background
 * 
 * ### Physical Cards vs Tokenized Cards
 * 
 * **Physical Cards:**
 * - Contain the actual PAN (Primary Account Number) issued by the bank
 * - Form Factor Indicator (9F6E) indicates physical card form (values 01-03 or "00")
 * - No Token Requestor ID present
 * 
 * **Tokenized/Digital Wallet Cards:**
 * - Contain a DPAN (Device Primary Account Number) - a token representing the real card
 * - Form Factor Indicator indicates mobile device (values 05-06 or "10")
 * - Token Requestor ID (9F19) identifies the wallet provider
 * - The actual PAN is never transmitted, only the DPAN
 * 
 * ### EMV Tags Used for Detection
 * 
 * | Tag   | Name                    | Description                                      |
 * |-------|-------------------------|--------------------------------------------------|
 * | 9F6E  | Form Factor Indicator   | Physical form of the payment device              |
 * | 9F19  | Token Requestor ID      | Identifies wallet provider (11 digits)           |
 * | 50    | Application Label       | May contain wallet-specific identifiers          |
 * | 9F65  | Track 2 Bit Map         | Can indicate tokenized transactions              |
 * 
 * ### Form Factor Indicator (9F6E) Values
 * 
 * **Physical Card Indicators:**
 * - 01: Standard card (ID-1 format)
 * - 02: Mini card (ID-000 format)
 * - 03: Non-card form factor
 * - 0x3030 (ASCII "00"): Physical card indication
 * 
 * **Mobile/Wearable Indicators:**
 * - 05: Mobile phone
 * - 06: Wearable device (watch, fitness band)
 * - 0x3130 (ASCII "10"): Mobile device indication
 * 
 * @see PaymentSource
 */
object CardSourceDetector {
    // EMV Tag identifiers
    internal const val TAG_FORM_FACTOR_INDICATOR = "9F6E"
    internal const val TAG_TOKEN_REQUESTOR_ID = "9F19"
    internal const val TAG_APPLICATION_LABEL = "50"
    internal const val TAG_TRACK2_BITMAP = "9F65"
    
    /**
     * Known Token Requestor IDs for major wallet providers.
     * 
     * These IDs are assigned by the card networks (Visa, Mastercard, etc.)
     * to identify which wallet provider tokenized a card.
     * 
     * Format: 11-digit numeric string
     */
    private object TokenRequestorIds {
        // Mastercard (MDES) Token Requestor IDs
        val MASTERCARD_APPLE_PAY = "50110030273"
        val MASTERCARD_GOOGLE_WALLET = "50120834693"
        val MASTERCARD_SAMSUNG_PAY = "50139059239"
        val MASTERCARD_GARMIN_PAY = "50148149593"
        val MASTERCARD_FITBIT_PAY = "50152609117"
        val MASTERCARD_MICROSOFT = "50148183749"
        
        // Visa (VTS) Token Requestor IDs
        val VISA_APPLE_PAY = "40010030273"
        val VISA_GOOGLE_WALLET = "40010075001"
        val VISA_SAMSUNG_PAY = "40010043095"
        val VISA_GARMIN_PAY = "40010075196"
        val VISA_FITBIT_PAY = "40010075839"
        
        // All Google Wallet IDs
        val GOOGLE_WALLET_IDS = setOf(
            MASTERCARD_GOOGLE_WALLET,
            VISA_GOOGLE_WALLET
        )
        
        // All Samsung Pay IDs
        val SAMSUNG_PAY_IDS = setOf(
            MASTERCARD_SAMSUNG_PAY,
            VISA_SAMSUNG_PAY
        )
        
        // All Apple Pay IDs
        val APPLE_PAY_IDS = setOf(
            MASTERCARD_APPLE_PAY,
            VISA_APPLE_PAY
        )
        
        // All Garmin Pay IDs
        val GARMIN_PAY_IDS = setOf(
            MASTERCARD_GARMIN_PAY,
            VISA_GARMIN_PAY
        )
        
        // All Fitbit Pay IDs
        val FITBIT_PAY_IDS = setOf(
            MASTERCARD_FITBIT_PAY,
            VISA_FITBIT_PAY
        )
    }
    
    /**
     * Visa Form Factor Indicator (9F6E) encoding:
     * 
     * Byte 1 - Consumer Device Form Factor:
     *   Bits 8-5 (upper nibble >> 4): Form Factor Type
     *     0x0 = Not applicable / Unknown
     *     0x1 = Card (standard ID-1 plastic card)
     *     0x2 = Mobile Phone
     *     0x3 = Watch / Wristband
     *     0x4 = Mobile Phone Case / Sleeve
     *     0x5 = Vehicle (car key, etc.)
     *     0x6 = Sticker
     *     0x7-0xF = Reserved
     *   
     *   Bit 4: Consumer Device Cardholder ID Method (CVM)
     *   Bits 3-1: Reserved
     * 
     * Bytes 2-4: Consumer device characteristics (varies by scheme)
     * 
     * Mastercard uses different encoding with explicit byte values.
     */
    private object FormFactorTypes {
        // Visa Form Factor (upper nibble of byte 1)
        const val VISA_CARD = 0x1
        const val VISA_MOBILE_PHONE = 0x2
        const val VISA_WATCH = 0x3
        const val VISA_PHONE_CASE = 0x4
        const val VISA_VEHICLE = 0x5
        const val VISA_STICKER = 0x6
        
        // Mobile device form factors (upper nibble values)
        val VISA_MOBILE_FORM_FACTORS = setOf(
            VISA_MOBILE_PHONE,  // 0x2 - Mobile phone
            VISA_WATCH,         // 0x3 - Watch/wristband  
            VISA_PHONE_CASE,    // 0x4 - Phone case
            VISA_VEHICLE,       // 0x5 - Vehicle key
            VISA_STICKER        // 0x6 - Sticker
        )
    }
    
    /**
     * Form Factor Indicator values indicating a physical card (Mastercard encoding).
     */
    private val PHYSICAL_CARD_FORM_FACTORS = setOf(
        byteArrayOf(0x01),                          // Standard card
        byteArrayOf(0x02),                          // Mini card
        byteArrayOf(0x03),                          // Non-card form factor (still physical)
        byteArrayOf(0x30, 0x30),                    // ASCII "00" - physical card
        byteArrayOf(0x30, 0x30, 0x30, 0x30),        // Extended format
    )
    
    /**
     * Form Factor Indicator values indicating a mobile/wearable device (Mastercard encoding).
     */
    private val MOBILE_FORM_FACTORS = setOf(
        byteArrayOf(0x05),                          // Mobile phone
        byteArrayOf(0x06),                          // Wearable device
        byteArrayOf(0x31, 0x30),                    // ASCII "10" - mobile device
        byteArrayOf(0x31, 0x30, 0x30, 0x30),        // Extended format
    )
    
    /**
     * Detailed detection result including raw EMV data used for detection.
     * Useful for debugging and logging.
     */
    data class DetectionResult(
        val source: PaymentSource,
        val formFactorIndicator: ByteArray? = null,
        val tokenRequestorId: String? = null,
        val applicationLabel: String? = null,
        val confidence: DetectionConfidence = DetectionConfidence.LOW,
        val debugInfo: String = ""
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DetectionResult) return false
            return source == other.source &&
                   formFactorIndicator?.contentEquals(other.formFactorIndicator) != false &&
                   tokenRequestorId == other.tokenRequestorId
        }
        
        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + (formFactorIndicator?.contentHashCode() ?: 0)
            result = 31 * result + (tokenRequestorId?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * Confidence level of the detection.
     */
    enum class DetectionConfidence {
        /** Detection based on multiple matching indicators */
        HIGH,
        /** Detection based on single strong indicator */
        MEDIUM,
        /** Detection based on heuristics or partial data */
        LOW
    }
    
    /**
     * Detect payment source from parsed TLV data.
     * 
     * @param tlvData Map of parsed EMV tags from card read operation
     * @return DetectionResult with identified source and metadata
     */
    fun detect(tlvData: Map<String, TlvParser.TlvData>): DetectionResult {
        // Extract relevant tags
        val formFactorIndicator = tlvData[TAG_FORM_FACTOR_INDICATOR]?.value
        val tokenRequestorIdRaw = tlvData[TAG_TOKEN_REQUESTOR_ID]?.value
        val applicationLabel = tlvData[TAG_APPLICATION_LABEL]?.value?.let { 
            String(it, Charsets.US_ASCII).trim()
        }
        
        // Convert Token Requestor ID to string (it's typically stored as numeric ASCII or BCD)
        val tokenRequestorId = tokenRequestorIdRaw?.let { parseTokenRequestorId(it) }
        
        val debugInfo = buildString {
            appendLine("Form Factor Indicator (9F6E): ${formFactorIndicator?.toHex() ?: "not present"}")
            appendLine("Token Requestor ID (9F19): ${tokenRequestorId ?: "not present"}")
            appendLine("Application Label (50): ${applicationLabel ?: "not present"}")
        }
        
        // Strategy 1: Check Token Requestor ID (most reliable for wallets)
        if (tokenRequestorId != null) {
            val walletFromToken = identifyWalletFromTokenRequestorId(tokenRequestorId)
            if (walletFromToken != PaymentSource.UNKNOWN) {
                return DetectionResult(
                    source = walletFromToken,
                    formFactorIndicator = formFactorIndicator,
                    tokenRequestorId = tokenRequestorId,
                    applicationLabel = applicationLabel,
                    confidence = DetectionConfidence.HIGH,
                    debugInfo = debugInfo
                )
            }
            
            // Has token requestor ID but unknown provider - it's still a wallet
            return DetectionResult(
                source = PaymentSource.OTHER_WALLET,
                formFactorIndicator = formFactorIndicator,
                tokenRequestorId = tokenRequestorId,
                applicationLabel = applicationLabel,
                confidence = DetectionConfidence.MEDIUM,
                debugInfo = debugInfo
            )
        }
        
        // Strategy 2: Check Form Factor Indicator
        if (formFactorIndicator != null && formFactorIndicator.isNotEmpty()) {
            val formFactorResult = analyzeFormFactor(formFactorIndicator, applicationLabel, debugInfo)
            if (formFactorResult != null) {
                return formFactorResult
            }
        }
        
        // Strategy 3: Check Application Label for wallet indicators
        if (applicationLabel != null) {
            val walletFromLabel = identifyWalletFromLabel(applicationLabel)
            
            if (walletFromLabel != null) {
                return DetectionResult(
                    source = walletFromLabel,
                    formFactorIndicator = formFactorIndicator,
                    applicationLabel = applicationLabel,
                    confidence = DetectionConfidence.LOW,
                    debugInfo = debugInfo
                )
            }
            
            // Check for generic wallet/pay indicators
            val normalizedLabel = applicationLabel.uppercase()
            if (normalizedLabel.contains("WALLET") || 
                (normalizedLabel.contains("PAY") && !normalizedLabel.contains("PREPAY"))) {
                return DetectionResult(
                    source = PaymentSource.OTHER_WALLET,
                    formFactorIndicator = formFactorIndicator,
                    applicationLabel = applicationLabel,
                    confidence = DetectionConfidence.LOW,
                    debugInfo = debugInfo
                )
            }
        }
        
        // Default: If no wallet indicators found, assume physical card
        // Most contactless reads without wallet indicators are physical cards
        return DetectionResult(
            source = PaymentSource.PHYSICAL_CARD,
            formFactorIndicator = formFactorIndicator,
            applicationLabel = applicationLabel,
            confidence = DetectionConfidence.LOW,
            debugInfo = debugInfo
        )
    }
    
    /**
     * Simplified detection that returns just the PaymentSource.
     * 
     * @param tlvData Map of parsed EMV tags from card read operation
     * @return Identified PaymentSource
     */
    fun detectSource(tlvData: Map<String, TlvParser.TlvData>): PaymentSource {
        return detect(tlvData).source
    }
    
    /**
     * Analyze Form Factor Indicator (9F6E) to determine payment source.
     * 
     * Handles multiple encoding formats:
     * 
     * ## Visa 4-byte Form Factor (most common):
     * - Byte 1: Consumer Device Form Factor (bits 7-5 = device type)
     * - Byte 2: Consumer Device Features
     *   - **Bit 8 (0x80): Network Connected flag** - KEY DIFFERENTIATOR!
     *     - 1 = Device is connected to network (mobile phone, smartwatch)
     *     - 0 = Device is NOT connected (physical card, sticker)
     *   - Other bits: Various features
     * - Bytes 3-4: Additional characteristics
     * 
     * Example values:
     * - Physical card: `20700000` → byte2=0x70 (bit8=0, not connected)
     * - Google Wallet: `238C0000` → byte2=0x8C (bit8=1, connected)
     * 
     * ## Mastercard encoding:
     * - Direct byte values: 0x01-0x03=Card, 0x05-0x06=Mobile
     * 
     * ## ASCII encoding:
     * - "00"=Card, "10"=Mobile
     */
    private fun analyzeFormFactor(
        formFactorIndicator: ByteArray,
        applicationLabel: String?,
        debugInfo: String
    ): DetectionResult? {
        val firstByte = formFactorIndicator[0].toInt() and 0xFF
        
        // Check Mastercard-style exact matches first
        val isPhysicalExact = PHYSICAL_CARD_FORM_FACTORS.any { it.contentEquals(formFactorIndicator) }
        val isMobileExact = MOBILE_FORM_FACTORS.any { it.contentEquals(formFactorIndicator) }
        
        if (isPhysicalExact) {
            return DetectionResult(
                source = PaymentSource.PHYSICAL_CARD,
                formFactorIndicator = formFactorIndicator,
                applicationLabel = applicationLabel,
                confidence = DetectionConfidence.HIGH,
                debugInfo = debugInfo
            )
        }
        
        if (isMobileExact) {
            // Try to identify specific wallet from application label
            val walletFromLabel = applicationLabel?.let { identifyWalletFromLabel(it) }
            
            // Without Token Requestor ID, we can only determine it's a mobile wallet
            val inferredWallet = walletFromLabel ?: PaymentSource.MOBILE_WALLET
            
            return DetectionResult(
                source = inferredWallet,
                formFactorIndicator = formFactorIndicator,
                applicationLabel = applicationLabel,
                confidence = if (walletFromLabel != null) DetectionConfidence.MEDIUM else DetectionConfidence.HIGH,
                debugInfo = debugInfo + "\nInferred from: Mastercard mobile form factor"
            )
        }
        
        // ===== VISA 4-BYTE FORM FACTOR ANALYSIS =====
        // This is the most reliable detection for Visa cards
        if (formFactorIndicator.size >= 2) {
            val byte2 = formFactorIndicator[1].toInt() and 0xFF
            val isNetworkConnected = (byte2 and 0x80) != 0  // Bit 8 of byte 2
            
            if (!isNetworkConnected) {
                // Device is NOT connected to network = Physical card
                return DetectionResult(
                    source = PaymentSource.PHYSICAL_CARD,
                    formFactorIndicator = formFactorIndicator,
                    applicationLabel = applicationLabel,
                    confidence = DetectionConfidence.HIGH,
                    debugInfo = debugInfo + "\nByte2=0x${byte2.toString(16)}, Network Connected=false"
                )
            } else {
                // Device IS connected to network = Mobile device / Wallet
                val visaFormFactor = (firstByte shr 4) and 0x0F
                val deviceType = when (visaFormFactor) {
                    FormFactorTypes.VISA_MOBILE_PHONE -> "Mobile Phone"
                    FormFactorTypes.VISA_WATCH -> "Watch/Wristband"
                    FormFactorTypes.VISA_PHONE_CASE -> "Phone Case"
                    FormFactorTypes.VISA_VEHICLE -> "Vehicle"
                    FormFactorTypes.VISA_STICKER -> "Sticker"
                    else -> "Mobile Device"
                }
                
                // Try to identify specific wallet from application label
                val walletFromLabel = applicationLabel?.let { identifyWalletFromLabel(it) }
                
                // Without Token Requestor ID, we can only determine it's a mobile wallet
                // but NOT which specific one (could be Google Wallet, Apple Pay, Samsung Pay, etc.)
                val inferredWallet = walletFromLabel ?: PaymentSource.MOBILE_WALLET
                
                val confidence = if (walletFromLabel != null) {
                    DetectionConfidence.MEDIUM
                } else {
                    DetectionConfidence.HIGH // High confidence it's a mobile wallet, just don't know which
                }
                
                return DetectionResult(
                    source = inferredWallet,
                    formFactorIndicator = formFactorIndicator,
                    applicationLabel = applicationLabel,
                    confidence = confidence,
                    debugInfo = debugInfo + "\nDevice Type: $deviceType\nByte2=0x${byte2.toString(16)}, Network Connected=true"
                )
            }
        }
        
        // ===== FALLBACK: Single byte form factors =====
        // Check Mastercard-style byte values (not nibble-based)
        if (firstByte in 0x01..0x03) {
            return DetectionResult(
                source = PaymentSource.PHYSICAL_CARD,
                formFactorIndicator = formFactorIndicator,
                applicationLabel = applicationLabel,
                confidence = DetectionConfidence.MEDIUM,
                debugInfo = debugInfo
            )
        }
        
        if (firstByte in 0x05..0x06) {
            val deviceType = if (firstByte == 0x05) "Mobile Phone" else "Wearable"
            
            // Try to identify specific wallet
            val walletFromLabel = applicationLabel?.let { identifyWalletFromLabel(it) }
            val inferredWallet = walletFromLabel ?: PaymentSource.MOBILE_WALLET
            
            return DetectionResult(
                source = inferredWallet,
                formFactorIndicator = formFactorIndicator,
                applicationLabel = applicationLabel,
                confidence = if (walletFromLabel != null) DetectionConfidence.MEDIUM else DetectionConfidence.HIGH,
                debugInfo = debugInfo + "\nDevice Type: $deviceType"
            )
        }
        
        return null
    }
    
    /**
     * Try to identify wallet from application label keywords.
     */
    private fun identifyWalletFromLabel(label: String): PaymentSource? {
        val normalizedLabel = label.uppercase()
        return when {
            normalizedLabel.contains("GOOGLE") || normalizedLabel.contains("GPAY") -> PaymentSource.GOOGLE_WALLET
            normalizedLabel.contains("SAMSUNG") || normalizedLabel.contains("SPAY") -> PaymentSource.SAMSUNG_PAY
            normalizedLabel.contains("APPLE") -> PaymentSource.APPLE_PAY
            normalizedLabel.contains("GARMIN") -> PaymentSource.GARMIN_PAY
            normalizedLabel.contains("FITBIT") -> PaymentSource.FITBIT_PAY
            else -> null
        }
    }
    
    /**
     * Identify wallet provider from Token Requestor ID.
     */
    private fun identifyWalletFromTokenRequestorId(tokenRequestorId: String): PaymentSource {
        return when (tokenRequestorId) {
            in TokenRequestorIds.GOOGLE_WALLET_IDS -> PaymentSource.GOOGLE_WALLET
            in TokenRequestorIds.SAMSUNG_PAY_IDS -> PaymentSource.SAMSUNG_PAY
            in TokenRequestorIds.APPLE_PAY_IDS -> PaymentSource.APPLE_PAY
            in TokenRequestorIds.GARMIN_PAY_IDS -> PaymentSource.GARMIN_PAY
            in TokenRequestorIds.FITBIT_PAY_IDS -> PaymentSource.FITBIT_PAY
            else -> PaymentSource.UNKNOWN
        }
    }
    
    /**
     * Parse Token Requestor ID from raw bytes.
     * Can be stored as ASCII string, BCD, or binary.
     */
    private fun parseTokenRequestorId(data: ByteArray): String? {
        if (data.isEmpty()) return null
        
        // Try ASCII interpretation first (most common)
        val ascii = String(data, Charsets.US_ASCII).trim()
        if (ascii.all { it.isDigit() } && ascii.length == 11) {
            return ascii
        }
        
        // Try BCD interpretation
        val bcd = data.decodeBcd()
        if (bcd.all { it.isDigit() } && bcd.length >= 11) {
            return bcd.take(11)
        }
        
        // Return hex as fallback
        return data.toHex()
    }
    
    /**
     * Extension to decode BCD-encoded bytes to string.
     */
    private fun ByteArray.decodeBcd(): String {
        val sb = StringBuilder()
        for (byte in this) {
            val high = (byte.toInt() shr 4) and 0x0F
            val low = byte.toInt() and 0x0F
            if (high < 10) sb.append(high)
            if (low < 10) sb.append(low)
        }
        return sb.toString()
    }
    
    /**
     * Extension to convert ByteArray to hex string.
     */
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}

