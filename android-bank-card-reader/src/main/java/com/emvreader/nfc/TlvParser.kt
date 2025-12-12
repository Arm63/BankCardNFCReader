package com.emvreader.nfc

/**
 * TLV (Tag-Length-Value) parser for EMV data structures
 */
internal object TlvParser {
    
    const val TAG_PAN = "5A"
    const val TAG_TRACK2 = "57"
    const val TAG_AID = "4F"
    const val TAG_DF_NAME = "84"
    const val TAG_PDOL = "9F38"
    const val TAG_AFL = "94"
    const val TAG_RESPONSE_FORMAT1 = "80"
    const val TAG_RESPONSE_FORMAT2 = "77"

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
                if (isConstructed(tag) && value.isNotEmpty()) {
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

    private fun isConstructed(tag: String): Boolean {
        if (tag.isEmpty()) return false
        val first = tag.substring(0, 2).toIntOrNull(16) ?: return false
        return (first and 0x20) != 0
    }

    fun extractPan(tlvMap: Map<String, TlvData>): String? {
        tlvMap[TAG_PAN]?.let { return decodeBcd(it.value) }
        tlvMap[TAG_TRACK2]?.let { return extractPanFromTrack2(it.value) }
        return null
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

    data class AflEntry(val sfi: Int, val firstRecord: Int, val lastRecord: Int)

    private fun List<Byte>.toHex(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}

