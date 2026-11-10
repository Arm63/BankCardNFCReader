package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmvCardReaderFlowTest {

    private val reader = EmvCardReader()

    private fun CardData.success(): CardData.Success {
        assertTrue("expected Success but was $this", this is CardData.Success)
        return this as CardData.Success
    }

    private fun CardData.error(): CardData.Error {
        assertTrue("expected Error but was $this", this is CardData.Error)
        return this as CardData.Error
    }

    @Test
    fun multiApp_readsHighestPriorityAppFirst() {
        val card = FakeCard(ppseFci(ppseEntry(VISA_AID, "VISA", 1), ppseEntry(MASTERCARD_AID, "MASTERCARD", 2)) + "9000")
            .install(VISA_AID, simpleApp(VISA_AID, "VISA", "4111111111111111", sfi = 1))
            .install(MASTERCARD_AID, simpleApp(MASTERCARD_AID, "MASTERCARD", "5555555555554444", sfi = 2))

        val result = reader.read(card).success()

        assertEquals("4111111111111111", result.pan)
    }

    @Test
    fun multiApp_fallsBackToNextApp_andReportsTheAppThatWasRead() {
        val card = FakeCard(
            ppseFci(
                ppseEntry(VISA_AID, "VISA", 1),
                ppseEntry(MASTERCARD_AID, "MASTERCARD", 2),
                ppseEntry(AMEX_AID, "AMEX", 3)
            ) + "9000"
        ).install(MASTERCARD_AID, simpleApp(MASTERCARD_AID, "MASTERCARD", "5555555555554444"))

        val result = reader.read(card).success()

        assertEquals("5555555555554444", result.pan)
        assertEquals(MASTERCARD_AID, result.aid)
    }

    @Test
    fun ppseMissing_fallsBackToKnownAids() {
        val card = FakeCard(ppseResponse = null)
            .install(VISA_AID, simpleApp(VISA_AID, "VISA", "4111111111111111"))

        assertEquals("4111111111111111", reader.read(card).success().pan)
    }

    @Test
    fun ppseMissing_readsMirViaKnownAid() {
        val card = FakeCard(ppseResponse = null)
            .install(MIR_AID, simpleApp(MIR_AID, "MIR", "2200000000000004"))

        assertEquals("2200000000000004", reader.read(card).success().pan)
    }

    @Test
    fun ppseMissing_andNoKnownApp_reportsPpseNotFound() {
        assertEquals(ErrorCode.PPSE_NOT_FOUND, reader.read(FakeCard(ppseResponse = null)).error().code)
    }

    @Test
    fun failedAppData_doesNotLeakIntoNextApp() {
        val googleTokenRequestor = berTlv("9F19", "40010075001F")
        val card = FakeCard(ppseFci(ppseEntry(VISA_AID, "VISA", 1), ppseEntry(MASTERCARD_AID, "MASTERCARD", 2)) + "9000")
            .install(
                VISA_AID,
                simpleApp(VISA_AID, "VISA", pan = null, gpoExtraTagsHex = googleTokenRequestor,
                    recordExtraTagsHex = berTlv("5F24", "281231"))
            )
            .install(MASTERCARD_AID, simpleApp(MASTERCARD_AID, "MASTERCARD", "5555555555554444", sfi = 2))

        val result = reader.read(card).success()

        assertEquals("5555555555554444", result.pan)
        assertEquals(PaymentSource.PHYSICAL_CARD, result.paymentSource)
        assertNull(result.expiryDate)
    }

    @Test
    fun linkLostAfterRecords_returnsPan_andStopsTalkingToCard() {
        val card = FakeCard(ppseFci(ppseEntry(VISA_AID, "VISA", 1)) + "9000")
            .install(VISA_AID, simpleApp(VISA_AID, "VISA", "4111111111111111"))
        card.dropLinkAfter = readRecordCmd(sfi = 1, record = 1)

        val result = reader.read(card).success()

        assertEquals("4111111111111111", result.pan)
        assertEquals(1, card.failedTransceives)
    }

    @Test
    fun linkLostBeforePan_reportsCommunicationError() {
        val card = FakeCard(ppseFci(ppseEntry(VISA_AID, "VISA", 1)) + "9000")
            .install(VISA_AID, simpleApp(VISA_AID, "VISA", "4111111111111111"))
        card.dropLinkAfter = FakeCard.selectCmd(VISA_AID)

        assertEquals(ErrorCode.COMMUNICATION_ERROR, reader.read(card).error().code)
    }

    @Test
    fun withoutPdol_gpoIsSentOnlyOnce() {
        val card = FakeCard(ppseFci(ppseEntry(VISA_AID, "VISA", 1)) + "9000")
            .install(VISA_AID, simpleApp(VISA_AID, "VISA", "4111111111111111", gpoResponse = "6985"))

        val result = reader.read(card).success()

        assertEquals("4111111111111111", result.pan)
        assertEquals(1, card.sent.count { it.startsWith("80A8") })
    }

    @Test
    fun arcaCardRunningMirApplication_isArca() {
        // Armenian ArCa card (PAN 9051…) whose only application is Mir's (A0000006581010, label "MIR").
        val card = FakeCard(ppseFci(ppseEntry(MIR_AID, priority = 1)) + "9000")
            .install(MIR_AID, simpleApp(MIR_AID, "MIR", "9051000000000009"))

        assertEquals(CardType.ARCA, reader.read(card).success().cardType)
    }

    @Test
    fun maestroPanOutsideBinTable_getsBrandFromAid() {
        val card = FakeCard(ppseFci(ppseEntry(MAESTRO_AID, "MAESTRO", 1)) + "9000")
            .install(MAESTRO_AID, simpleApp(MAESTRO_AID, "MAESTRO", "6759649826438453"))

        assertEquals(CardType.MASTERCARD, reader.read(card).success().cardType)
    }
}
