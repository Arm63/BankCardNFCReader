package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlvPaddingAndPpseTest {

    @Test
    fun parse_skipsPaddingBetweenDataObjects() {
        val parsed = TlvParser.parse(hexBytes("00" + "5A084111111111111111" + "FFFF"))
        assertEquals("4111111111111111", TlvParser.extractPan(parsed))
    }

    @Test
    fun parse_skipsPaddingInsideTemplate() {
        // A single '00' (a pair would accidentally parse as an empty tag "00").
        val parsed = TlvParser.parse(hexBytes(berTlv("70", "00" + "5A084111111111111111")))
        assertEquals("4111111111111111", TlvParser.extractPan(parsed))
    }

    @Test
    fun parsePpseApplications_returnsEveryAppSortedByPriority() {
        val apps = TlvParser.parsePpseApplications(
            hexBytes(ppseFci(ppseEntry(MASTERCARD_AID, "MASTERCARD", 2), ppseEntry(VISA_AID, "VISA DEBIT", 1)))
        )
        assertEquals(listOf(VISA_AID, MASTERCARD_AID), apps.map { it.aidHex })
        assertEquals(listOf("VISA DEBIT", "MASTERCARD"), apps.map { it.label })
    }

    @Test
    fun parsePpseApplications_appsWithoutPriorityGoLast_inCardOrder() {
        val apps = TlvParser.parsePpseApplications(
            hexBytes(ppseFci(ppseEntry(AMEX_AID), ppseEntry(MIR_AID), ppseEntry(VISA_AID, priority = 1)))
        )
        assertEquals(listOf(VISA_AID, AMEX_AID, MIR_AID), apps.map { it.aidHex })
    }

    @Test
    fun parsePpseApplications_doesNotReturnPpseNameAsApp() {
        val apps = TlvParser.parsePpseApplications(hexBytes(ppseFci(ppseEntry(VISA_AID, "VISA", 1))))
        assertTrue(apps.none { it.aidHex == PPSE_NAME_HEX })
        assertEquals(1, apps.size)
    }
}
