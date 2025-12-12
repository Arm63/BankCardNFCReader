package com.example.bankcardreader

import com.example.bankcardreader.nfc.EmvUtils
import com.example.bankcardreader.nfc.TlvParser
import org.junit.Assert.*
import org.junit.Test

class TlvParserTest {

    @Test
    fun parse_simpleTlv_returnsParsedData() {
        // Tag 5A (PAN), Length 08, Value: 4111111111111111 (BCD encoded)
        val data = EmvUtils.hexStringToByteArray("5A0841111111111111FF")
        val result = TlvParser.parse(data)
        
        assertTrue(result.containsKey("5A"))
        assertEquals(8, result["5A"]?.length)
    }

    @Test
    fun parse_nestedTlv_returnsAllTags() {
        // Constructed tag 70 containing primitive tags
        val data = EmvUtils.hexStringToByteArray("70095A044111111157049876")
        val result = TlvParser.parse(data)
        
        assertTrue(result.containsKey("70"))
        assertTrue(result.containsKey("5A"))
        assertTrue(result.containsKey("57"))
    }

    @Test
    fun extractPan_fromTag5A_returnsPan() {
        val data = EmvUtils.hexStringToByteArray("5A0841111111111111FF")
        val result = TlvParser.parse(data)
        val pan = TlvParser.extractPan(result)
        
        assertEquals("4111111111111111", pan)
    }

    @Test
    fun extractPan_fromTrack2_returnsPan() {
        // Track 2: PAN + D + Expiry + Service Code
        val data = EmvUtils.hexStringToByteArray("570B4111111111111111D2512")
        val result = TlvParser.parse(data)
        val pan = TlvParser.extractPan(result)
        
        assertEquals("4111111111111111", pan)
    }

    @Test
    fun extractAfl_format1Response_returnsEntries() {
        // Format 1 (tag 80): AIP (2 bytes) + AFL
        val data = EmvUtils.hexStringToByteArray("800A0800080101000110010100")
        val result = TlvParser.parse(data)
        val aflEntries = TlvParser.extractAfl(result)
        
        assertTrue(aflEntries.isNotEmpty())
    }

    @Test
    fun parse_multiByteTag_handlesCorrectly() {
        // Two-byte tag 9F38 (PDOL)
        val data = EmvUtils.hexStringToByteArray("9F38039F0206")
        val result = TlvParser.parse(data)
        
        assertTrue(result.containsKey("9F38"))
    }

    @Test
    fun parse_emptyData_returnsEmptyMap() {
        val result = TlvParser.parse(byteArrayOf())
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractPan_noPanTag_returnsNull() {
        val data = EmvUtils.hexStringToByteArray("500B56495341204352454449")
        val result = TlvParser.parse(data)
        val pan = TlvParser.extractPan(result)
        
        assertNull(pan)
    }
}

