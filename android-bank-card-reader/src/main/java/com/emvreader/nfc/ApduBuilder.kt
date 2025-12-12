package com.emvreader.nfc

/**
 * APDU command builder for EMV card communication
 */
internal object ApduBuilder {

    /**
     * SELECT PPSE command
     */
    fun selectPpse(): ByteArray = select(EmvConstants.PPSE)

    /**
     * SELECT command for AID
     */
    fun select(aid: ByteArray): ByteArray = byteArrayOf(
        0x00,              // CLA
        0xA4.toByte(),     // INS: SELECT
        0x04,              // P1: Select by DF name
        0x00,              // P2: First occurrence
        aid.size.toByte(), // Lc
        *aid,              // Data
        0x00               // Le
    )

    /**
     * GET PROCESSING OPTIONS command
     */
    fun gpo(pdolData: ByteArray?): ByteArray {
        val data = if (pdolData != null && pdolData.isNotEmpty()) {
            byteArrayOf(0x83.toByte(), pdolData.size.toByte(), *pdolData)
        } else {
            byteArrayOf(0x83.toByte(), 0x00)
        }
        
        return byteArrayOf(
            0x80.toByte(),     // CLA
            0xA8.toByte(),     // INS: GPO
            0x00,              // P1
            0x00,              // P2
            data.size.toByte(),// Lc
            *data,             // Data
            0x00               // Le
        )
    }

    /**
     * READ RECORD command
     */
    fun readRecord(recordNumber: Int, sfi: Int): ByteArray = byteArrayOf(
        0x00,                                // CLA
        0xB2.toByte(),                       // INS: READ RECORD
        recordNumber.toByte(),               // P1: Record number
        ((sfi shl 3) or 0x04).toByte(),     // P2: SFI with bit 3 set
        0x00                                 // Le
    )
}

