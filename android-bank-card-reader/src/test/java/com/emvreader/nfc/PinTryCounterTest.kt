package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinTryCounterTest {

    @Test
    fun extractPinTryCounter_singleByte() {
        val map = mapOf(
            TlvParser.TAG_PIN_TRY_COUNTER to TlvParser.TlvData(
                TlvParser.TAG_PIN_TRY_COUNTER,
                1,
                byteArrayOf(0x03)
            )
        )
        assertEquals(3, TlvParser.extractPinTryCounter(map))
    }

    @Test
    fun extractPinTryCounter_missing_returnsNull() {
        assertNull(TlvParser.extractPinTryCounter(emptyMap()))
    }

    @Test
    fun success_pinTriesRemaining_storedOnSuccess() {
        val s = CardData.Success(
            pan = "4111111111111111",
            formattedPan = "4111 1111 1111 1111",
            maskedPan = "4111 **** **** 1111",
            cardType = CardType.VISA,
            pinTriesRemaining = 2
        )
        assertEquals(2, s.pinTriesRemaining)
    }
}
