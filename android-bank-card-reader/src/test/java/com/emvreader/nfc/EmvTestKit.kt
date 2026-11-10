package com.emvreader.nfc

import java.io.IOException

// Test-only helpers for building EMV byte strings and a scripted contactless card.

internal fun hexBytes(hex: String): ByteArray =
    hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

internal fun asciiHex(s: String): String = s.toByteArray(Charsets.US_ASCII).toHexString()

/** BER-TLV with a correct length field (short form, 0x81 or 0x82). */
internal fun berTlv(tag: String, valueHex: String): String {
    val len = valueHex.length / 2
    val lengthField = when {
        len < 0x80 -> "%02X".format(len)
        len < 0x100 -> "81%02X".format(len)
        else -> "82%04X".format(len)
    }
    return tag + lengthField + valueHex
}

internal fun tlvMap(vararg tags: Pair<String, String>): Map<String, TlvParser.TlvData> =
    tags.associate { (tag, valueHex) -> tag to TlvParser.TlvData(tag, valueHex.length / 2, hexBytes(valueHex)) }

internal const val PPSE_NAME_HEX = "325041592E5359532E4444463031"
internal const val VISA_AID = "A0000000031010"
internal const val MASTERCARD_AID = "A0000000041010"
internal const val MAESTRO_AID = "A0000000043060"
internal const val AMEX_AID = "A00000002501"
internal const val MIR_AID = "A0000006581010"
internal const val GPO_NO_PDOL = "80A8000002830000"

/** One `61` directory entry inside a PPSE response. */
internal fun ppseEntry(aidHex: String, label: String? = null, priority: Int? = null): String =
    berTlv(
        "61",
        berTlv("4F", aidHex) +
            (label?.let { berTlv("50", asciiHex(it)) } ?: "") +
            (priority?.let { berTlv("87", "%02X".format(it)) } ?: "")
    )

/** PPSE FCI (without status word). */
internal fun ppseFci(vararg entries: String): String =
    berTlv("6F", berTlv("84", PPSE_NAME_HEX) + berTlv("A5", berTlv("BF0C", entries.joinToString(""))))

internal fun readRecordCmd(sfi: Int, record: Int): String = "00B2%02X%02X00".format(record, (sfi shl 3) or 0x04)

/**
 * Script for a minimal payment application: SELECT → GPO (format 2, one AFL record) → record with PAN.
 * Pass [pan] = null for an app whose record carries no PAN.
 */
internal fun simpleApp(
    aidHex: String,
    label: String,
    pan: String?,
    sfi: Int = 1,
    gpoExtraTagsHex: String = "",
    recordExtraTagsHex: String = "",
    gpoResponse: String? = null
): Map<String, String> = mapOf(
    FakeCard.SELECT to berTlv("6F", berTlv("84", aidHex) + berTlv("A5", berTlv("50", asciiHex(label)))) + "9000",
    GPO_NO_PDOL to (gpoResponse
        ?: (berTlv("77", berTlv("82", "0000") + berTlv("94", "%02X010100".format(sfi shl 3)) + gpoExtraTagsHex) + "9000")),
    readRecordCmd(sfi, 1) to berTlv("70", (pan?.let { berTlv("5A", it) } ?: "") + recordExtraTagsHex) + "9000"
)

/**
 * Scripted contactless card. SELECT by name switches the current application (partial-name
 * matching, like real cards); every other command is answered from the selected app's script.
 * Unknown commands answer `6A82`.
 */
internal class FakeCard(private val ppseResponse: String?) : ApduTransport {
    private val apps = linkedMapOf<String, Map<String, String>>()
    private var selected: String? = null
    private var linkDown = false

    /** Every command the reader managed to send, uppercase hex. */
    val sent = mutableListOf<String>()

    /** After this command is answered, the "card" disappears and every transceive throws. */
    var dropLinkAfter: String? = null

    /** Number of transceive calls that hit a dead link. */
    var failedTransceives = 0
        private set

    fun install(aidHex: String, script: Map<String, String>) = apply { apps[aidHex] = script }

    override fun transceive(command: ByteArray): ByteArray {
        if (linkDown) {
            failedTransceives++
            throw IOException("Transceive failed")
        }
        val cmd = command.toHexString()
        sent += cmd
        val response = if (cmd.startsWith("00A40400")) select(cmd) else {
            selected?.let { apps[it]?.get(cmd) } ?: "6A82"
        }
        if (cmd == dropLinkAfter) linkDown = true
        return hexBytes(response)
    }

    private fun select(cmd: String): String {
        val lc = cmd.substring(8, 10).toInt(16)
        val name = cmd.substring(10, 10 + lc * 2)
        if (name == PPSE_NAME_HEX) {
            selected = null
            return ppseResponse ?: "6A82"
        }
        selected = apps.keys.firstOrNull { it.startsWith(name) }
        return selected?.let { apps.getValue(it)[SELECT] } ?: "6A82"
    }

    companion object {
        const val SELECT = "SELECT"
        fun selectCmd(aidHex: String): String = "00A40400%02X%s00".format(aidHex.length / 2, aidHex)
    }
}
