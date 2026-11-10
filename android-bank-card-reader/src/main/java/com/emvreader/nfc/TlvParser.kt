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

    /** PPSE directory entry template (one per application). */
    const val TAG_DIRECTORY_ENTRY = "61"
    /** Application Priority Indicator — low nibble 1 (highest) .. 15; 0 = no priority. */
    const val TAG_APPLICATION_PRIORITY = "87"

    data class TlvData(val tag: String, val length: Int, val value: ByteArray)

    /**
     * Flattens all data objects (including nested ones) into a map keyed by tag.
     * When a tag occurs more than once, the last occurrence wins — use [parsePpseApplications]
     * for the PPSE directory, where tags `4F`/`50`/`87` repeat per application.
     */
    fun parse(data: ByteArray): Map<String, TlvData> {
        val result = mutableMapOf<String, TlvData>()
        for (tlv in readLevel(data)) {
            result[tlv.tag] = tlv
            if (isEmvConstructed(tlv.tag) && tlv.value.isNotEmpty()) {
                result.putAll(parse(tlv.value))
            }
        }
        return result
    }

    /**
     * Data objects at one nesting level, in card order (duplicates kept).
     * Skips `00` / `FF` padding bytes, which ISO 7816-4 allows before, between and after objects.
     * Stops at the first malformed object.
     */
    private fun readLevel(data: ByteArray): List<TlvData> {
        val result = mutableListOf<TlvData>()
        var offset = 0
        while (offset < data.size) {
            val first = data[offset].toInt() and 0xFF
            if (first == 0x00 || first == 0xFF) {
                offset++
                continue
            }
            val (tlv, next) = (
                try {
                    readOne(data, offset)
                } catch (e: Exception) {
                    null
                }
                ) ?: break
            result += tlv
            offset = next
        }
        return result
    }

    private fun readOne(data: ByteArray, start: Int): Pair<TlvData, Int>? {
        val (tag, tagEnd) = parseTag(data, start)
        if (tagEnd >= data.size) return null
        val (length, lengthEnd) = parseLength(data, tagEnd)
        if (length < 0 || lengthEnd + length > data.size) return null
        return TlvData(tag, length, data.copyOfRange(lengthEnd, lengthEnd + length)) to lengthEnd + length
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

    /**
     * One payment application listed in the PPSE directory.
     *
     * @property aidHex Application identifier (tag `4F`), uppercase hex.
     * @property label Application label (tag `50`), if present.
     * @property priority 1 (highest) .. 15 from tag `87`; **null** when absent or "no priority".
     */
    data class PpseApplication(val aidHex: String, val label: String?, val priority: Int?)

    /**
     * Every application in a PPSE response (`61` entries), highest priority first.
     * Applications without a priority keep their card order after the prioritised ones.
     * The PPSE's own DF name (`84`) is never returned.
     */
    fun parsePpseApplications(ppseResponse: ByteArray): List<PpseApplication> {
        val entries = mutableListOf<PpseApplication>()
        collectDirectoryEntries(readLevel(ppseResponse), entries)
        return entries.sortedBy { it.priority ?: Int.MAX_VALUE } // stable: ties keep card order
    }

    private fun collectDirectoryEntries(level: List<TlvData>, out: MutableList<PpseApplication>) {
        for (tlv in level) {
            if (tlv.tag == TAG_DIRECTORY_ENTRY) {
                val fields = readLevel(tlv.value).associateBy { it.tag }
                val aid = fields[TAG_AID]?.value?.takeIf { it.isNotEmpty() } ?: continue
                out += PpseApplication(
                    aidHex = aid.toHex(),
                    label = fields[TAG_APPLICATION_LABEL]?.value
                        ?.let { String(it, Charsets.ISO_8859_1).trim() }
                        ?.takeIf { it.isNotEmpty() },
                    priority = fields[TAG_APPLICATION_PRIORITY]?.value?.firstOrNull()
                        ?.let { it.toInt() and 0x0F }
                        ?.takeIf { it != 0 }
                )
            } else if (isEmvConstructed(tlv.tag)) {
                collectDirectoryEntries(readLevel(tlv.value), out)
            }
        }
    }

    private fun List<Byte>.toHex(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
