package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class ExpiryDateTest {

    private fun tlv(hex: String): Map<String, TlvParser.TlvData> = mapOf(
        TlvParser.TAG_EXPIRY to TlvParser.TlvData(
            tag = TlvParser.TAG_EXPIRY,
            length = hex.length / 2,
            value = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        )
    )

    @Test
    fun extractExpiry_validBcd_returnsYearAndMonth() {
        val e = TlvParser.extractExpiry(tlv("271231"))
        assertEquals(ExpiryDate(2027, 12), e)
    }

    @Test
    fun extractExpiry_missing_returnsNull() {
        assertNull(TlvParser.extractExpiry(emptyMap()))
    }

    @Test
    fun extractExpiry_shortValue_returnsNull() {
        assertNull(TlvParser.extractExpiry(tlv("2712")))
    }

    @Test
    fun extractExpiry_invalidMonth_returnsNull() {
        assertNull(TlvParser.extractExpiry(tlv("271331")))
    }

    @Test
    fun displayMmYy_padsZero() {
        assertEquals("01/27", ExpiryDate(2027, 1).displayMmYy())
        assertEquals("12/05", ExpiryDate(2005, 12).displayMmYy())
    }

    @Test
    fun displayMmYyyy_full() {
        assertEquals("03/2029", ExpiryDate(2029, 3).displayMmYyyy())
    }

    @Test
    fun isExpired_pastMonth_true() {
        val now = GregorianCalendar(2026, Calendar.MAY, 16)
        assertTrue(ExpiryDate(2025, 12).isExpired(now))
        assertTrue(ExpiryDate(2026, 4).isExpired(now))
    }

    @Test
    fun isExpired_currentMonth_false() {
        val now = GregorianCalendar(2026, Calendar.MAY, 16)
        assertFalse(ExpiryDate(2026, 5).isExpired(now))
    }

    @Test
    fun isExpired_futureMonth_false() {
        val now = GregorianCalendar(2026, Calendar.MAY, 16)
        assertFalse(ExpiryDate(2026, 6).isExpired(now))
        assertFalse(ExpiryDate(2030, 1).isExpired(now))
    }

    @Test
    fun success_expiryDate_storedOnSuccess() {
        val s = CardData.Success(
            pan = "4111111111111111",
            formattedPan = "4111 1111 1111 1111",
            maskedPan = "4111 **** **** 1111",
            cardType = CardType.VISA,
            expiryDate = ExpiryDate(2028, 6)
        )
        assertEquals(ExpiryDate(2028, 6), s.expiryDate)
        assertEquals("06/28", s.expiryDate?.displayMmYy())
    }
}
