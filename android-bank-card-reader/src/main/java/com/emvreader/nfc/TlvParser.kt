package com.emvreader.nfc

/**
 * TLV (Tag-Length-Value) parser for EMV data structures.
 * 
 * Parses BER-TLV encoded data as defined in EMV specifications.
 * Supports nested/constructed tags and extracts payment-relevant data.
 */
object TlvParser {
    
    // Card identity tags
    const val TAG_PAN = "5A"
    /** EMV: Cardholder Name (often LASTNAME/FIRSTNAME, space-padded). May be absent on contactless. */
    const val TAG_CARDHOLDER_NAME = "5F20"
    /** Track 1 Data — may hold ISO 7813-style ^SURNAME/FIRSTNAME^ between separators when 5F20 is absent */
    const val TAG_TRACK1 = "56"
    const val TAG_TRACK2 = "57"
    const val TAG_AID = "4F"
    const val TAG_DF_NAME = "84"
    const val TAG_APPLICATION_LABEL = "50"
    
    // Processing tags
    const val TAG_PDOL = "9F38"
    const val TAG_AFL = "94"
    const val TAG_RESPONSE_FORMAT1 = "80"
    const val TAG_RESPONSE_FORMAT2 = "77"
    
    // Card source detection tags (for physical vs digital wallet detection)
    /** Form Factor Indicator - indicates physical card vs mobile device */
    const val TAG_FORM_FACTOR_INDICATOR = "9F6E"
    /** Token Requestor ID - identifies wallet provider (Google, Samsung, Apple) */
    const val TAG_TOKEN_REQUESTOR_ID = "9F19"
    /** Track 2 Bit Map for Additional Data - may indicate tokenization */
    const val TAG_TRACK2_BITMAP = "9F65"
    /** Device Type - additional device identification */
    const val TAG_DEVICE_TYPE = "9F6D"
    /** PIN Try Counter — offline PIN attempts remaining (often via GET DATA). */
    const val TAG_PIN_TRY_COUNTER = "9F17"
    /** Application Expiration Date — YYMMDD BCD (3 bytes). */
    const val TAG_EXPIRY = "5F24"

    data class TlvData(val tag: String, val length: Int, val value: ByteArray)

    fun parse(data: ByteArray): Map<String, TlvData> {
        val result = mutableMapOf<String, TlvData>()
        var offset = 0

        while (offset < data.size) {
            try {
                val (tag, tagEnd) = parseTag(data, offset)
                offset = tagEnd
                if (offset >= data.size) break

                val (length, lengthEnd) = parseLength(data, offset)
                offset = lengthEnd
                if (offset + length > data.size) break

                val value = data.copyOfRange(offset, offset + length)
                offset += length

                result[tag] = TlvData(tag, length, value)

                // Parse nested constructed tags
                if (isEmvConstructed(tag) && value.isNotEmpty()) {
                    result.putAll(parse(value))
                }
            } catch (e: Exception) {
                break
            }
        }

        return result
    }

    private fun parseTag(data: ByteArray, start: Int): Pair<String, Int> {
        var offset = start
        val tagBytes = mutableListOf<Byte>()
        val first = data[offset++]
        tagBytes.add(first)

        if ((first.toInt() and 0x1F) == 0x1F) {
            while (offset < data.size) {
                val next = data[offset++]
                tagBytes.add(next)
                if ((next.toInt() and 0x80) == 0) break
            }
        }

        return Pair(tagBytes.toHex(), offset)
    }

    private fun parseLength(data: ByteArray, start: Int): Pair<Int, Int> {
        var offset = start
        val first = data[offset++].toInt() and 0xFF

        return if (first <= 0x7F) {
            Pair(first, offset)
        } else {
            val numBytes = first and 0x7F
            var length = 0
            repeat(numBytes) {
                length = (length shl 8) or (data[offset++].toInt() and 0xFF)
            }
            Pair(length, offset)
        }
    }

    /**
     * EMV multi-byte data object tags (5Fxx, 9Fxx) are primitive values even when BER-TLV
     * bit 6 of the first byte would suggest "constructed".
     */
    internal fun isConstructedTagForEmv(tag: String): Boolean = isEmvConstructed(tag)

    private fun isEmvConstructed(tag: String): Boolean {
        if (tag.isEmpty()) return false
        if (tag.length >= 4 &&
            (tag.startsWith("5F", ignoreCase = true) || tag.startsWith("9F", ignoreCase = true))
        ) {
            return false
        }
        val first = tag.substring(0, 2).toIntOrNull(16) ?: return false
        return (first and 0x20) != 0
    }

    fun extractPan(tlvMap: Map<String, TlvData>): String? {
        tlvMap[TAG_PAN]?.let { return decodeBcd(it.value) }
        tlvMap[TAG_TRACK2]?.let { return extractPanFromTrack2(it.value) }
        return null
    }

    fun extractCardholderName(tlvMap: Map<String, TlvData>): String? {
        tlvMap[TAG_CARDHOLDER_NAME]?.value?.let { v ->
            meaningfulCardholderField(String(v, Charsets.ISO_8859_1))?.let { return it }
        }
        tlvMap[TAG_TRACK1]?.value?.let { parseCardholderFromTrack1(it) }?.let { return it }
        return null
    }

    /**
     * Contactless often returns tag 5F20 as a placeholder (e.g. two bytes `20 2F` → `" /"`) with no letters.
     * Treat those like missing data so callers can fall back to Track 1 or GET DATA.
     */
    internal fun meaningfulCardholderField(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (!t.any { it.isLetter() }) return null
        return t
    }

    private fun parseCardholderFromTrack1(bytes: ByteArray): String? {
        val s = String(bytes, Charsets.US_ASCII).trim { it <= ' ' || it == '%' }
        if (s.isEmpty()) return null
        val parts = s.split('^')
        if (parts.size < 2) return null
        val nameField = parts[1].trim()
        if (nameField.isEmpty()) return null
        if (!nameField.any { it.isLetter() }) return null
        return nameField
    }

    private fun decodeBcd(data: ByteArray): String {
        val sb = StringBuilder()
        for (byte in data) {
            val high = (byte.toInt() shr 4) and 0x0F
            val low = byte.toInt() and 0x0F
            if (high < 10) sb.append(high)
            if (low < 10) sb.append(low)
        }
        return sb.toString()
    }

    private fun extractPanFromTrack2(data: ByteArray): String? {
        val hex = data.toHex()
        val separator = hex.indexOf('D')
        return if (separator > 0) hex.substring(0, separator) else null
    }

    fun extractAfl(tlvMap: Map<String, TlvData>): List<AflEntry> {
        // Format 1 response
        tlvMap[TAG_RESPONSE_FORMAT1]?.let { tlv ->
            if (tlv.value.size > 2) {
                return parseAflData(tlv.value.copyOfRange(2, tlv.value.size))
            }
            return emptyList()
        }

        // Format 2 response
        tlvMap[TAG_AFL]?.let { return parseAflData(it.value) }
        return emptyList()
    }

    private fun parseAflData(data: ByteArray): List<AflEntry> {
        val entries = mutableListOf<AflEntry>()
        var offset = 0

        while (offset + 4 <= data.size) {
            val sfi = (data[offset].toInt() and 0xFF) shr 3
            val first = data[offset + 1].toInt() and 0xFF
            val last = data[offset + 2].toInt() and 0xFF
            entries.add(AflEntry(sfi, first, last))
            offset += 4
        }

        return entries
    }

    fun extractPdol(tlvMap: Map<String, TlvData>): ByteArray? = tlvMap[TAG_PDOL]?.value

    /** Application Identifier from tag `4F` (uppercase hex, no spaces). */
    fun extractAid(tlvMap: Map<String, TlvData>): String? =
        tlvMap[TAG_AID]?.value?.toHex()?.uppercase()?.takeIf { it.isNotEmpty() }

    /**
     * PIN try counter from tag `9F17` (first byte, 0–255). Often absent on contactless.
     */
    fun extractPinTryCounter(tlvMap: Map<String, TlvData>): Int? {
        val v = tlvMap[TAG_PIN_TRY_COUNTER]?.value ?: return null
        if (v.isEmpty()) return null
        return v[0].toInt() and 0xFF
    }

    /**
     * Expiry from tag `5F24` (YYMMDD BCD, 3 bytes). Returns null if tag missing,
     * malformed, or month outside 1..12.
     *
     * Two-digit year mapped to 2000–2099 (EMV cards use 4-digit Gregorian year minus century).
     */
    fun extractExpiry(tlvMap: Map<String, TlvData>): ExpiryDate? {
        val v = tlvMap[TAG_EXPIRY]?.value ?: return null
        if (v.size < 3) return null
        val digits = decodeBcd(v)
        if (digits.length < 4) return null
        val yy = digits.substring(0, 2).toIntOrNull() ?: return null
        val mm = digits.substring(2, 4).toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        return ExpiryDate(year = 2000 + yy, month = mm)
    }

    data class AflEntry(val sfi: Int, val firstRecord: Int, val lastRecord: Int)

    private fun List<Byte>.toHex(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
