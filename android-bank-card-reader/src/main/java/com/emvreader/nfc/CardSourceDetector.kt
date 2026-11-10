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
 * - Form Factor Indicator (9F6E) reports a card form factor
 * - No Token Requestor ID present
 *
 * **Tokenized/Digital Wallet Cards:**
 * - Contain a DPAN (Device Primary Account Number) - a token representing the real card
 * - Form Factor Indicator reports a phone / wearable
 * - Token Requestor ID (9F19) identifies the wallet provider
 * - The actual PAN is never transmitted, only the DPAN
 *
 * ### EMV Tags Used for Detection
 *
 * | Tag   | Name                    | Description                                      |
 * |-------|-------------------------|--------------------------------------------------|
 * | 9F19  | Token Requestor ID      | Identifies wallet provider (n11)                 |
 * | 9F6E  | Form Factor Indicator   | Layout depends on the scheme (see below)         |
 * | 50    | Application Label       | May contain wallet-specific identifiers          |
 * | 4F/84 | AID / DF name           | RID selects the 9F6E layout                      |
 *
 * ### Tag 9F6E is scheme-specific
 *
 * **Visa (RID A000000003) — Form Factor Indicator, 4 bytes:**
 * - Byte 1, bits 8-6: version (`001`); bits 5-1: consumer device form factor —
 *   0 standard card, 1 mini card, 2 non-card form factor (key fob, sticker),
 *   3 mobile phone, 4 wrist-worn device, higher values: other consumer devices
 * - Byte 2: device features — b8 passcode capable, b7 signature panel, b6 hologram,
 *   b5 CVV2, b4 two-way messaging, b3 cloud-based (HCE) credentials, b2 biometric CVM
 * - Examples: `20 70 …` = card with signature panel + hologram + CVV2;
 *   `23 8C …` = phone, passcode + messaging + cloud credentials (Google Wallet HCE)
 *
 * **Mastercard (RID A000000004) — Third Party Data:**
 * - Country code (2) | Unique identifier (2) | Device type (2, ASCII digits, present
 *   when bit 8 of the first unique-identifier byte is 0) | proprietary data
 * - Device type `"00"` = card; any other value = a non-card device
 *
 * Other schemes: only exact legacy encodings below are recognised.
 *
 * @see PaymentSource
 */
object CardSourceDetector {
    // EMV Tag identifiers
    internal const val TAG_FORM_FACTOR_INDICATOR = "9F6E"
    internal const val TAG_TOKEN_REQUESTOR_ID = "9F19"
    internal const val TAG_APPLICATION_LABEL = "50"
    internal const val TAG_TRACK2_BITMAP = "9F65"

    private const val RID_VISA = "A000000003"
    private const val RID_MASTERCARD = "A000000004"

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

    /** Visa form factor (byte 1, bits 5-1) → description. Values 0..2 are physical. */
    private val VISA_FORM_FACTORS = mapOf(
        0x00 to "Standard card",
        0x01 to "Mini card",
        0x02 to "Non-card form factor",
        0x03 to "Mobile phone",
        0x04 to "Wrist-worn device"
    )
    private const val VISA_LAST_PHYSICAL_FORM_FACTOR = 0x02
    private const val VISA_FFI_VERSION_1 = 0x01

    /**
     * Exact legacy 9F6E values treated as a physical card (any scheme).
     */
    private val PHYSICAL_CARD_FORM_FACTORS = setOf(
        byteArrayOf(0x01),                          // Standard card
        byteArrayOf(0x02),                          // Mini card
        byteArrayOf(0x03),                          // Non-card form factor (still physical)
        byteArrayOf(0x30, 0x30),                    // ASCII "00" - physical card
        byteArrayOf(0x30, 0x30, 0x30, 0x30),        // Extended format
    )

    /**
     * Exact legacy 9F6E values treated as a mobile/wearable device (any scheme).
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
                   formFactorIndicator.contentEquals(other.formFactorIndicator) &&
                   tokenRequestorId == other.tokenRequestorId &&
                   applicationLabel == other.applicationLabel &&
                   confidence == other.confidence &&
                   debugInfo == other.debugInfo
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + formFactorIndicator.contentHashCode()
            result = 31 * result + (tokenRequestorId?.hashCode() ?: 0)
            result = 31 * result + (applicationLabel?.hashCode() ?: 0)
            result = 31 * result + confidence.hashCode()
            result = 31 * result + debugInfo.hashCode()
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
     * @param tlvData Map of parsed EMV tags from card read operation. Include the selected
     *                application's `4F` or `84` so tag 9F6E can be decoded per scheme.
     * @return DetectionResult with identified source and metadata
     */
    fun detect(tlvData: Map<String, TlvParser.TlvData>): DetectionResult {
        // Extract relevant tags
        val formFactorIndicator = tlvData[TAG_FORM_FACTOR_INDICATOR]?.value
        val tokenRequestorIdRaw = tlvData[TAG_TOKEN_REQUESTOR_ID]?.value
        val applicationLabel = tlvData[TAG_APPLICATION_LABEL]?.value?.let {
            String(it, Charsets.US_ASCII).trim()
        }
        val rid = (tlvData[TlvParser.TAG_AID] ?: tlvData[TlvParser.TAG_DF_NAME])
            ?.value?.toHex()?.take(10)

        // Convert Token Requestor ID to string (it's typically stored as numeric ASCII or BCD)
        val tokenRequestorId = tokenRequestorIdRaw?.let { parseTokenRequestorId(it) }

        val debugInfo = buildString {
            appendLine("Form Factor Indicator (9F6E): ${formFactorIndicator?.toHex() ?: "not present"}")
            appendLine("Token Requestor ID (9F19): ${tokenRequestorId ?: "not present"}")
            appendLine("Application Label (50): ${applicationLabel ?: "not present"}")
            appendLine("RID: ${rid ?: "not present"}")
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
            val formFactorResult = analyzeFormFactor(formFactorIndicator, rid, applicationLabel, debugInfo)
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

            // Generic wallet words — whole words only, so "UnionPay", "RuPay" or
            // "PayPass" (physical card labels) don't count.
            val words = labelWords(applicationLabel)
            if ("WALLET" in words || "PAY" in words) {
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
     * Analyze tag 9F6E using the layout of the card's scheme (see class docs).
     * Returns null when the value carries no usable signal.
     */
    private fun analyzeFormFactor(
        formFactorIndicator: ByteArray,
        rid: String?,
        applicationLabel: String?,
        debugInfo: String
    ): DetectionResult? {
        if (PHYSICAL_CARD_FORM_FACTORS.any { it.contentEquals(formFactorIndicator) }) {
            return physicalCard(formFactorIndicator, applicationLabel, DetectionConfidence.HIGH, debugInfo)
        }
        if (MOBILE_FORM_FACTORS.any { it.contentEquals(formFactorIndicator) }) {
            return mobileWallet(
                formFactorIndicator, applicationLabel, DetectionConfidence.HIGH,
                debugInfo + "\nInferred from: legacy mobile form factor value"
            )
        }

        val isVisaLayout = rid == RID_VISA ||
            (rid != RID_MASTERCARD && formFactorIndicator.size == 4 &&
                (formFactorIndicator[0].toInt() and 0xFF) shr 5 == VISA_FFI_VERSION_1)
        return when {
            isVisaLayout -> analyzeVisaFormFactor(formFactorIndicator, applicationLabel, debugInfo)
            rid == RID_MASTERCARD -> analyzeMastercardThirdPartyData(formFactorIndicator, applicationLabel, debugInfo)
            else -> null
        }
    }

    private fun analyzeVisaFormFactor(
        ffi: ByteArray,
        applicationLabel: String?,
        debugInfo: String
    ): DetectionResult {
        val formFactor = ffi[0].toInt() and 0x1F
        val features = ffi.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        val device = VISA_FORM_FACTORS[formFactor] ?: "Consumer device (form factor $formFactor)"
        val info = debugInfo + "\nDevice Type: $device (Visa FFI), features=0x%02X".format(features)

        return if (formFactor <= VISA_LAST_PHYSICAL_FORM_FACTOR) {
            physicalCard(ffi, applicationLabel, DetectionConfidence.HIGH, info)
        } else {
            mobileWallet(ffi, applicationLabel, DetectionConfidence.HIGH, info)
        }
    }

    private fun analyzeMastercardThirdPartyData(
        data: ByteArray,
        applicationLabel: String?,
        debugInfo: String
    ): DetectionResult? {
        // Country Code (2) | Unique Identifier (2) | Device Type (2, only when UID b16 == 0)
        if (data.size < 6 || (data[2].toInt() and 0x80) != 0) return null
        val deviceType = String(data, 4, 2, Charsets.US_ASCII)
        if (!deviceType.all { it.isDigit() }) return null
        val info = debugInfo + "\nDevice Type: \"$deviceType\" (Mastercard third party data)"

        return if (deviceType == "00") {
            physicalCard(data, applicationLabel, DetectionConfidence.HIGH, info)
        } else {
            mobileWallet(data, applicationLabel, DetectionConfidence.MEDIUM, info)
        }
    }

    private fun physicalCard(
        ffi: ByteArray,
        applicationLabel: String?,
        confidence: DetectionConfidence,
        debugInfo: String
    ) = DetectionResult(
        source = PaymentSource.PHYSICAL_CARD,
        formFactorIndicator = ffi,
        applicationLabel = applicationLabel,
        confidence = confidence,
        debugInfo = debugInfo
    )

    /**
     * Device form factor without a Token Requestor ID: the label may name the wallet,
     * otherwise it is a [PaymentSource.MOBILE_WALLET].
     */
    private fun mobileWallet(
        ffi: ByteArray,
        applicationLabel: String?,
        confidenceWithoutLabel: DetectionConfidence,
        debugInfo: String
    ): DetectionResult {
        val walletFromLabel = applicationLabel?.let { identifyWalletFromLabel(it) }
        return DetectionResult(
            source = walletFromLabel ?: PaymentSource.MOBILE_WALLET,
            formFactorIndicator = ffi,
            applicationLabel = applicationLabel,
            confidence = if (walletFromLabel != null) DetectionConfidence.MEDIUM else confidenceWithoutLabel,
            debugInfo = debugInfo
        )
    }

    private fun labelWords(label: String): Set<String> =
        label.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }.toSet()

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
     * Can be stored as ASCII string, BCD (EMV format n11 in 6 bytes: right-justified with a
     * leading `0` nibble, or left-justified with a trailing `F`), or binary.
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
            val padding = bcd.length - 11
            return if (padding > 0 && bcd.take(padding).all { it == '0' }) bcd.takeLast(11) else bcd.take(11)
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
