package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardholderNameTest {

    @Test
    fun isConstructedTagForEmv_5F20_isPrimitive() {
        assertFalse(TlvParser.isConstructedTagForEmv("5F20"))
    }

    @Test
    fun isConstructedTagForEmv_70_isConstructed() {
        assertTrue(TlvParser.isConstructedTagForEmv("70"))
    }

    @Test
    fun extractCardholderName_readsAsatryanArmenStyle() {
        val raw = "ASATRYAN/ARMEN                        "
        val map = mapOf(
            TlvParser.TAG_CARDHOLDER_NAME to TlvParser.TlvData(
                TlvParser.TAG_CARDHOLDER_NAME,
                raw.length,
                raw.toByteArray(Charsets.ISO_8859_1)
            )
        )
        assertEquals("ASATRYAN/ARMEN", TlvParser.extractCardholderName(map))
    }

    @Test
    fun extractCardholderName_readsTag5F20() {
        val raw = "DOE/JOHN      "
        val map = mapOf(
            TlvParser.TAG_CARDHOLDER_NAME to TlvParser.TlvData(
                TlvParser.TAG_CARDHOLDER_NAME,
                raw.length,
                raw.toByteArray(Charsets.ISO_8859_1)
            )
        )
        assertEquals("DOE/JOHN", TlvParser.extractCardholderName(map))
    }

    @Test
    fun extractCardholderName_missing_returnsNull() {
        assertNull(TlvParser.extractCardholderName(emptyMap()))
    }

    /** Visa contactless GPO sometimes exposes 5F20 as `20 2F` (space + slash) — not a real name. */
    @Test
    fun extractCardholderName_placeholder5F20_ignoresAndFallsBackToTrack1() {
        val placeholder = byteArrayOf(0x20, 0x2F)
        val track1 = "%B4578900000447065^DOE/JOHN^25011200000000000999".toByteArray(Charsets.US_ASCII)
        val map = mapOf(
            TlvParser.TAG_CARDHOLDER_NAME to TlvParser.TlvData(
                TlvParser.TAG_CARDHOLDER_NAME,
                placeholder.size,
                placeholder
            ),
            TlvParser.TAG_TRACK1 to TlvParser.TlvData(TlvParser.TAG_TRACK1, track1.size, track1)
        )
        assertEquals("DOE/JOHN", TlvParser.extractCardholderName(map))
    }

    @Test
    fun extractCardholderName_placeholder5F20_only_returnsNull() {
        val placeholder = byteArrayOf(0x20, 0x2F)
        val map = mapOf(
            TlvParser.TAG_CARDHOLDER_NAME to TlvParser.TlvData(
                TlvParser.TAG_CARDHOLDER_NAME,
                placeholder.size,
                placeholder
            )
        )
        assertNull(TlvParser.extractCardholderName(map))
    }

    @Test
    fun extractCardholderName_fallsBackToTrack1Tag56() {
        val track1 = "%B4578900000447065^DOE/JOHN^25011200000000000999".toByteArray(Charsets.US_ASCII)
        val map = mapOf(
            TlvParser.TAG_TRACK1 to TlvParser.TlvData(
                TlvParser.TAG_TRACK1,
                track1.size,
                track1
            )
        )
        assertEquals("DOE/JOHN", TlvParser.extractCardholderName(map))
    }

    @Test
    fun extractCardholderName_tag5F20PreferredOverTrack1() {
        val name5f20 = "SMITH/ALICE      "
        val track1 = "%B4111111111111111^DOE/JOHN^2501".toByteArray(Charsets.US_ASCII)
        val map = mapOf(
            TlvParser.TAG_CARDHOLDER_NAME to TlvParser.TlvData(
                TlvParser.TAG_CARDHOLDER_NAME,
                name5f20.length,
                name5f20.toByteArray(Charsets.ISO_8859_1)
            ),
            TlvParser.TAG_TRACK1 to TlvParser.TlvData(TlvParser.TAG_TRACK1, track1.size, track1)
        )
        assertEquals("SMITH/ALICE", TlvParser.extractCardholderName(map))
    }

    @Test
    fun maskedOwnerName_splitsWordsAndSlash() {
        val s = sampleSuccess(cardholderName = "Ann Marie")
        assertEquals("A**** M****", s.maskedOwnerName())
    }

    @Test
    fun maskedOwnerName_lastFirstWithSlash() {
        val s = sampleSuccess(cardholderName = "DOE/JOHN")
        assertEquals("D**** J****", s.maskedOwnerName())
    }

    @Test
    fun maskedOwnerName_nullOrBlank_returnsNull() {
        assertNull(sampleSuccess(cardholderName = null).maskedOwnerName())
        assertNull(sampleSuccess(cardholderName = "   ").maskedOwnerName())
    }

    private fun sampleSuccess(cardholderName: String?) = CardData.Success(
        pan = "4111111111111111",
        formattedPan = "4111 1111 1111 1111",
        maskedPan = "4111 **** **** 1111",
        cardType = CardType.VISA,
        cardholderName = cardholderName
    )
}
