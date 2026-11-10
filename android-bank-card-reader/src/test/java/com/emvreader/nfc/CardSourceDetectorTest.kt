package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSourceDetectorTest {

    private fun detect(vararg tags: Pair<String, String>) = CardSourceDetector.detect(tlvMap(*tags))

    // --- Application label heuristic ---

    @Test
    fun unionPayLabel_isPhysicalCard() {
        assertEquals(PaymentSource.PHYSICAL_CARD, detect("50" to asciiHex("UnionPay Debit")).source)
    }

    @Test
    fun ruPayLabel_isPhysicalCard() {
        assertEquals(PaymentSource.PHYSICAL_CARD, detect("50" to asciiHex("RuPay Debit")).source)
    }

    @Test
    fun payPassLabel_isPhysicalCard() {
        assertEquals(PaymentSource.PHYSICAL_CARD, detect("50" to asciiHex("PayPass")).source)
    }

    @Test
    fun walletWordInLabel_isOtherWallet() {
        assertEquals(PaymentSource.OTHER_WALLET, detect("50" to asciiHex("Bank Wallet")).source)
    }

    // --- Visa Form Factor Indicator (9F6E) ---

    @Test
    fun visaFfi_standardCardWithPasscodeBit_isPhysicalCard() {
        assertEquals(PaymentSource.PHYSICAL_CARD, detect("84" to VISA_AID, "9F6E" to "20F00000").source)
    }

    @Test
    fun visaFfi_mobilePhone_isMobileWallet() {
        assertEquals(PaymentSource.MOBILE_WALLET, detect("84" to VISA_AID, "9F6E" to "238C0000").source)
    }

    @Test
    fun visaFfi_wristWorn_isReportedAsWearable() {
        val result = detect("84" to VISA_AID, "9F6E" to "248C0000")
        assertEquals(PaymentSource.MOBILE_WALLET, result.source)
        assertTrue(result.debugInfo, result.debugInfo.contains("Wrist-worn"))
    }

    // --- Mastercard Third Party Data (9F6E) ---

    @Test
    fun mastercardThirdPartyData_deviceTypeCard_isPhysicalCard() {
        // Country 0484 | Unique Identifier 0000 | Device Type "00"
        assertEquals(PaymentSource.PHYSICAL_CARD, detect("84" to MASTERCARD_AID, "9F6E" to "048400003030").source)
    }

    @Test
    fun mastercardThirdPartyData_nonCardDevice_isMobileWallet() {
        assertEquals(PaymentSource.MOBILE_WALLET, detect("84" to MASTERCARD_AID, "9F6E" to "048400003031").source)
    }

    // --- Token Requestor ID (9F19) ---

    @Test
    fun tokenRequestorId_fPadded_isGoogleWallet() {
        assertEquals(PaymentSource.GOOGLE_WALLET, detect("9F19" to "40010075001F").source)
    }

    @Test
    fun tokenRequestorId_numericLeadingZero_isGoogleWallet() {
        // EMV format n11 in 6 bytes: right-justified with a leading zero nibble.
        assertEquals(PaymentSource.GOOGLE_WALLET, detect("9F19" to "040010075001").source)
    }

    // --- DetectionResult equality ---

    @Test
    fun detectionResult_equalityIsSymmetric_andHashConsistent() {
        val a = CardSourceDetector.DetectionResult(PaymentSource.PHYSICAL_CARD, formFactorIndicator = null)
        val b = CardSourceDetector.DetectionResult(PaymentSource.PHYSICAL_CARD, formFactorIndicator = hexBytes("01"))
        assertEquals(a == b, b == a)
        if (a == b) assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun detectionResult_sameContent_isEqual() {
        val a = CardSourceDetector.DetectionResult(PaymentSource.PHYSICAL_CARD, formFactorIndicator = hexBytes("2070"))
        val b = CardSourceDetector.DetectionResult(PaymentSource.PHYSICAL_CARD, formFactorIndicator = hexBytes("2070"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
