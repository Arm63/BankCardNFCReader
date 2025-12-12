package com.example.bankcardreader

import com.example.bankcardreader.nfc.EmvUtils
import org.junit.Assert.*
import org.junit.Test

class EmvUtilsTest {

    @Test
    fun buildSelectPpseCommand_returnsValidApdu() {
        val command = EmvUtils.buildSelectPpseCommand()
        
        // Verify APDU structure: CLA INS P1 P2 Lc Data Le
        assertEquals(0x00.toByte(), command[0]) // CLA
        assertEquals(0xA4.toByte(), command[1]) // INS (SELECT)
        assertEquals(0x04.toByte(), command[2]) // P1 (by name)
        assertEquals(0x00.toByte(), command[3]) // P2
        
        // PPSE = "2PAY.SYS.DDF01" = 14 bytes
        assertEquals(14.toByte(), command[4]) // Lc
    }

    @Test
    fun buildSelectCommand_withVisaAid_returnsValidApdu() {
        val visaAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        val command = EmvUtils.buildSelectCommand(visaAid)
        
        assertEquals(0x00.toByte(), command[0]) // CLA
        assertEquals(0xA4.toByte(), command[1]) // INS
        assertEquals(7.toByte(), command[4]) // Lc (AID length)
    }

    @Test
    fun buildGpoCommand_emptyPdol_returnsValidApdu() {
        val command = EmvUtils.buildGpoCommand(null)
        
        assertEquals(0x80.toByte(), command[0]) // CLA
        assertEquals(0xA8.toByte(), command[1]) // INS (GET PROCESSING OPTIONS)
        assertEquals(0x02.toByte(), command[4]) // Lc (length of 83 00)
        assertEquals(0x83.toByte(), command[5]) // Tag 83
        assertEquals(0x00.toByte(), command[6]) // Length 0
    }

    @Test
    fun buildReadRecordCommand_returnsValidApdu() {
        val command = EmvUtils.buildReadRecordCommand(1, 1)
        
        assertEquals(0x00.toByte(), command[0]) // CLA
        assertEquals(0xB2.toByte(), command[1]) // INS (READ RECORD)
        assertEquals(0x01.toByte(), command[2]) // P1 (record 1)
        assertEquals(0x0C.toByte(), command[3]) // P2 (SFI 1 << 3 | 0x04)
    }

    @Test
    fun isSuccessResponse_sw9000_returnsTrue() {
        val response = byteArrayOf(0x6F, 0x00, 0x90.toByte(), 0x00)
        assertTrue(EmvUtils.isSuccessResponse(response))
    }

    @Test
    fun isSuccessResponse_sw6A82_returnsFalse() {
        val response = byteArrayOf(0x6A, 0x82.toByte())
        assertFalse(EmvUtils.isSuccessResponse(response))
    }

    @Test
    fun getResponseData_withStatusWords_returnsDataOnly() {
        val response = byteArrayOf(0x6F, 0x10, 0x84, 0x07, 0x90.toByte(), 0x00)
        val data = EmvUtils.getResponseData(response)
        
        assertEquals(4, data.size)
        assertEquals(0x6F.toByte(), data[0])
    }

    @Test
    fun toHexString_convertsCorrectly() {
        val bytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03)
        assertEquals("A000000003", EmvUtils.toHexString(bytes))
    }

    @Test
    fun hexStringToByteArray_convertsCorrectly() {
        val hex = "A000000003"
        val bytes = EmvUtils.hexStringToByteArray(hex)
        
        assertEquals(5, bytes.size)
        assertEquals(0xA0.toByte(), bytes[0])
        assertEquals(0x03.toByte(), bytes[4])
    }
}

