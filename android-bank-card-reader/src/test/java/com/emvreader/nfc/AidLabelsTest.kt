package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AidLabelsTest {

    @Test
    fun extractAid_returnsUppercaseHex() {
        val aid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        val map = mapOf(
            TlvParser.TAG_AID to TlvParser.TlvData(TlvParser.TAG_AID, aid.size, aid)
        )
        assertEquals("A0000000031010", TlvParser.extractAid(map))
    }

    @Test
    fun aidLabels_visaCreditDebit() {
        assertEquals("Visa Credit/Debit", AidLabels.displayName("A0000000031010"))
    }

    @Test
    fun aidLabels_mastercard() {
        assertEquals("Mastercard", AidLabels.displayName("A0000000041010"))
    }

    @Test
    fun aidLabels_unknown_returnsNull() {
        assertNull(AidLabels.displayName("DEADBEEF"))
    }

    @Test
    fun success_aidDisplayName_delegatesToAidLabels() {
        val s = CardData.Success(
            pan = "4111111111111111",
            formattedPan = "4111 1111 1111 1111",
            maskedPan = "4111 **** **** 1111",
            cardType = CardType.VISA,
            aid = "A0000000031010"
        )
        assertEquals("Visa Credit/Debit", s.aidDisplayName)
    }
}
