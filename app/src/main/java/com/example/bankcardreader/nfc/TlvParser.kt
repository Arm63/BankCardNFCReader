package com.example.bankcardreader.nfc

/**
 * TLV (Tag-Length-Value) parser for EMV data structures.
 * 
 * EMV data is encoded using BER-TLV (Basic Encoding Rules - Tag Length Value).
 * Tags can be 1-3 bytes, Length can be 1-3 bytes, Value is variable.
 */
object TlvParser {

    // EMV Tags relevant for PAN extraction
    const val TAG_PAN = "5A"                    // Application PAN (Primary Account Number)
    const val TAG_TRACK2 = "57"                 // Track 2 Equivalent Data
    const val TAG_CARDHOLDER_NAME = "5F20"      // Cardholder Name
    const val TAG_PAN_SEQUENCE = "5F34"         // PAN Sequence Number
    const val TAG_EXPIRY_DATE = "5F24"          // Application Expiration Date
    const val TAG_AID = "4F"                    // Application Identifier (DF Name)
    const val TAG_APPLICATION_LABEL = "50"     // Application Label
    const val TAG_PDOL = "9F38"                 // Processing Options Data Object List
    const val TAG_AFL = "94"                    // Application File Locator
    const val TAG_FCI_TEMPLATE = "6F"           // File Control Information Template
    const val TAG_DF_NAME = "84"                // DF Name
    const val TAG_FCI_PROPRIETARY = "A5"        // FCI Proprietary Template
    const val TAG_APP_TEMPLATE = "61"           // Application Template
    const val TAG_RESPONSE_FORMAT1 = "80"       // Response Message Template Format 1
    const val TAG_RESPONSE_FORMAT2 = "77"       // Response Message Template Format 2
    const val TAG_RECORD_TEMPLATE = "70"        // EMV Proprietary Template

    data class TlvData(
        val tag: String,
        val length: Int,
        val value: ByteArray
    ) {
        override fun toString(): String {
            return "TLV(tag=$tag, len=$length, value=${EmvUtils.toHexString(value)})"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TlvData
            return tag == other.tag && length == other.length && value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            var result = tag.hashCode()
            result = 31 * result + length
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    /**
     * Parse TLV data from byte array
     * Returns a map of tag -> TlvData for easy lookup
     */
    fun parse(data: ByteArray): Map<String, TlvData> {
        val result = mutableMapOf<String, TlvData>()
        var offset = 0

        while (offset < data.size) {
            try {
                // Parse Tag (1-3 bytes)
                val tagResult = parseTag(data, offset)
                val tag = tagResult.first
                offset = tagResult.second

                if (offset >= data.size) break

                // Parse Length (1-3 bytes)
                val lengthResult = parseLength(data, offset)
                val length = lengthResult.first
                offset = lengthResult.second

                if (offset + length > data.size) break

                // Extract Value
                val value = data.copyOfRange(offset, offset + length)
                offset += length

                val tlv = TlvData(tag, length, value)
                result[tag] = tlv

                // Recursively parse constructed tags
                if (isConstructedTag(tag) && value.isNotEmpty()) {
                    val nestedTlvs = parse(value)
                    result.putAll(nestedTlvs)
                }
            } catch (e: Exception) {
                break
            }
        }

        return result
    }

    /**
     * Parse tag bytes and return (tagHex, newOffset)
     */
    private fun parseTag(data: ByteArray, startOffset: Int): Pair<String, Int> {
        var offset = startOffset
        val tagBytes = mutableListOf<Byte>()

        // First byte of tag
        val firstByte = data[offset++]
        tagBytes.add(firstByte)

        // Check if tag is multi-byte (bits 1-5 of first byte are all 1s)
        if ((firstByte.toInt() and 0x1F) == 0x1F) {
            // Multi-byte tag - continue reading while bit 8 is set
            while (offset < data.size) {
                val nextByte = data[offset++]
                tagBytes.add(nextByte)
                if ((nextByte.toInt() and 0x80) == 0) break
            }
        }

        val tagHex = tagBytes.joinToString("") { "%02X".format(it) }
        return Pair(tagHex, offset)
    }

    /**
     * Parse length bytes and return (length, newOffset)
     * Supports definite short form (1 byte) and definite long form (2-3 bytes)
     */
    private fun parseLength(data: ByteArray, startOffset: Int): Pair<Int, Int> {
        var offset = startOffset
        val firstByte = data[offset++].toInt() and 0xFF

        return if (firstByte <= 0x7F) {
            // Short form: length is in single byte
            Pair(firstByte, offset)
        } else {
            // Long form: first byte indicates number of length bytes
            val numLengthBytes = firstByte and 0x7F
            var length = 0
            for (i in 0 until numLengthBytes) {
                length = (length shl 8) or (data[offset++].toInt() and 0xFF)
            }
            Pair(length, offset)
        }
    }

    /**
     * Check if tag represents a constructed (container) type
     * Bit 6 of first tag byte indicates constructed (1) vs primitive (0)
     */
    private fun isConstructedTag(tag: String): Boolean {
        if (tag.isEmpty()) return false
        val firstByte = tag.substring(0, 2).toInt(16)
        return (firstByte and 0x20) != 0
    }

    /**
     * Extract PAN from parsed TLV data
     * Tries Tag 5A first, then falls back to Tag 57 (Track 2)
     */
    fun extractPan(tlvMap: Map<String, TlvData>): String? {
        // Try direct PAN tag (5A)
        tlvMap[TAG_PAN]?.let { tlv ->
            return decodePan(tlv.value)
        }

        // Fallback to Track 2 Equivalent Data (57)
        tlvMap[TAG_TRACK2]?.let { tlv ->
            return extractPanFromTrack2(tlv.value)
        }

        return null
    }

    /**
     * Decode PAN from BCD-encoded bytes
     * PAN is stored as packed BCD (Binary Coded Decimal)
     * Each nibble represents one digit, 'F' is padding
     */
    private fun decodePan(data: ByteArray): String {
        val sb = StringBuilder()
        for (byte in data) {
            val high = (byte.toInt() shr 4) and 0x0F
            val low = byte.toInt() and 0x0F

            if (high < 10) sb.append(high)
            if (low < 10) sb.append(low)
        }
        return sb.toString()
    }

    /**
     * Extract PAN from Track 2 Equivalent Data
     * Format: PAN + 'D' + Expiry + Service Code + Discretionary Data
     * 'D' is the field separator (encoded as 0xD in BCD)
     */
    private fun extractPanFromTrack2(data: ByteArray): String? {
        val track2Hex = EmvUtils.toHexString(data)
        // Find separator 'D' and take everything before it as PAN
        val separatorIndex = track2Hex.indexOf('D')
        return if (separatorIndex > 0) {
            track2Hex.substring(0, separatorIndex)
        } else {
            null
        }
    }

    /**
     * Extract Application File Locator (AFL) from GPO response
     * AFL tells us which records to read from the card
     */
    fun extractAfl(tlvMap: Map<String, TlvData>): List<AflEntry> {
        val aflEntries = mutableListOf<AflEntry>()
        
        // Check for Format 1 response (tag 80)
        tlvMap[TAG_RESPONSE_FORMAT1]?.let { tlv ->
            // Format 1: AIP (2 bytes) + AFL
            if (tlv.value.size > 2) {
                val aflData = tlv.value.copyOfRange(2, tlv.value.size)
                aflEntries.addAll(parseAflData(aflData))
            }
            return aflEntries
        }

        // Check for Format 2 response (tag 77) with AFL tag 94
        tlvMap[TAG_AFL]?.let { tlv ->
            aflEntries.addAll(parseAflData(tlv.value))
        }

        return aflEntries
    }

    /**
     * Parse AFL data into entries
     * Each entry is 4 bytes: SFI, First Record, Last Record, Offline Records
     */
    private fun parseAflData(data: ByteArray): List<AflEntry> {
        val entries = mutableListOf<AflEntry>()
        var offset = 0

        while (offset + 4 <= data.size) {
            val sfi = (data[offset].toInt() and 0xFF) shr 3
            val firstRecord = data[offset + 1].toInt() and 0xFF
            val lastRecord = data[offset + 2].toInt() and 0xFF
            // val offlineRecords = data[offset + 3].toInt() and 0xFF // Not needed for PAN

            entries.add(AflEntry(sfi, firstRecord, lastRecord))
            offset += 4
        }

        return entries
    }

    /**
     * Extract PDOL (Processing Options Data Object List) from FCI
     * PDOL defines what data the card needs for GET PROCESSING OPTIONS
     */
    fun extractPdol(tlvMap: Map<String, TlvData>): ByteArray? {
        return tlvMap[TAG_PDOL]?.value
    }

    data class AflEntry(
        val sfi: Int,
        val firstRecord: Int,
        val lastRecord: Int
    )
}

