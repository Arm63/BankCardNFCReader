package com.emvreader.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTypeTest {

    @Test
    fun detect_arcaBin_isArca() {
        assertEquals(CardType.ARCA, CardType.detect("9051000000000009"))
    }

    @Test
    fun detect_mirBin_staysMir() {
        assertEquals(CardType.MIR, CardType.detect("2200000000000004"))
    }

    @Test
    fun fromAid_mirRid_isMir() {
        assertEquals(CardType.MIR, CardType.fromAid("A0000006581010"))
    }
}
