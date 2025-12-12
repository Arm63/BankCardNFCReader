package com.example.bankcardreader

import com.example.bankcardreader.nfc.CardValidator
import org.junit.Assert.*
import org.junit.Test

class CardValidatorTest {

    @Test
    fun luhn_validVisa_returnsTrue() {
        assertTrue(CardValidator.isValidLuhn("4111111111111111"))
    }

    @Test
    fun luhn_validMastercard_returnsTrue() {
        assertTrue(CardValidator.isValidLuhn("5500000000000004"))
    }

    @Test
    fun luhn_validAmex_returnsTrue() {
        assertTrue(CardValidator.isValidLuhn("378282246310005"))
    }

    @Test
    fun luhn_invalidNumber_returnsFalse() {
        assertFalse(CardValidator.isValidLuhn("4111111111111112"))
    }

    @Test
    fun luhn_tooShort_returnsFalse() {
        assertFalse(CardValidator.isValidLuhn("411111"))
    }

    @Test
    fun detectCardType_visa_returnsVisa() {
        assertEquals(CardValidator.CardType.VISA, CardValidator.detectCardType("4111111111111111"))
    }

    @Test
    fun detectCardType_mastercard_returnsMastercard() {
        assertEquals(CardValidator.CardType.MASTERCARD, CardValidator.detectCardType("5500000000000004"))
    }

    @Test
    fun detectCardType_amex_returnsAmex() {
        assertEquals(CardValidator.CardType.AMEX, CardValidator.detectCardType("378282246310005"))
    }

    @Test
    fun detectCardType_discover_returnsDiscover() {
        assertEquals(CardValidator.CardType.DISCOVER, CardValidator.detectCardType("6011111111111117"))
    }

    @Test
    fun detectCardType_mir_returnsMir() {
        assertEquals(CardValidator.CardType.MIR, CardValidator.detectCardType("2200000000000004"))
    }

    @Test
    fun formatCardNumber_standard_returnsFormatted() {
        assertEquals("4111 1111 1111 1111", CardValidator.formatCardNumber("4111111111111111"))
    }

    @Test
    fun formatCardNumber_amex_returnsAmexFormat() {
        assertEquals("3782 822463 10005", CardValidator.formatCardNumber("378282246310005"))
    }

    @Test
    fun maskCardNumber_standard_returnsMasked() {
        assertEquals("**** **** **** 1111", CardValidator.maskCardNumber("4111111111111111"))
    }

    @Test
    fun cleanCardNumber_withSpaces_returnsDigitsOnly() {
        assertEquals("4111111111111111", CardValidator.cleanCardNumber("4111 1111 1111 1111"))
    }

    @Test
    fun isValidCardNumber_validCard_returnsTrue() {
        assertTrue(CardValidator.isValidCardNumber("4111111111111111"))
    }

    @Test
    fun isValidCardNumber_invalidCard_returnsFalse() {
        assertFalse(CardValidator.isValidCardNumber("4111111111111112"))
    }
}

