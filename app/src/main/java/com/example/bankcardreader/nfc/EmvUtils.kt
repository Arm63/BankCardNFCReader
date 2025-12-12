package com.example.bankcardreader.nfc

/**
 * EMV APDU commands and utilities for reading contactless payment cards.
 * 
 * EMV (Europay, Mastercard, Visa) is the global standard for chip card payments.
 * APDU (Application Protocol Data Unit) is the communication format between card and reader.
 */
object EmvUtils {

    // Common Application Identifiers (AIDs) for payment cards
    val KNOWN_AIDS = listOf(
        // Visa
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10),
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x20, 0x10),
        // Mastercard
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10),
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x30, 0x60),
        // Amex
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x25, 0x01, 0x01),
        // Discover
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x01, 0x52, 0x30, 0x10),
        // UnionPay
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x03, 0x33, 0x01, 0x01),
        // JCB
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x65, 0x10, 0x10),
        // Mir (Russian)
        byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x06, 0x58.toByte(), 0x01, 0x01),
    )

    // Payment System Environment (PSE) for contact cards
    val PSE = "1PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)
    
    // Proximity Payment System Environment (PPSE) for contactless cards
    val PPSE = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)

    /**
     * Build SELECT command for Payment System Environment
     * CLA=00, INS=A4, P1=04 (select by name), P2=00
     */
    fun buildSelectPpseCommand(): ByteArray {
        return buildSelectCommand(PPSE)
    }

    /**
     * Build SELECT command for a specific AID
     */
    fun buildSelectCommand(aid: ByteArray): ByteArray {
        return byteArrayOf(
            0x00,                   // CLA: Class byte
            0xA4.toByte(),          // INS: SELECT instruction
            0x04,                   // P1: Select by DF name
            0x00,                   // P2: First or only occurrence
            aid.size.toByte(),      // Lc: Length of AID
            *aid,                   // Data: AID bytes
            0x00                    // Le: Expected response length (0 = max)
        )
    }

    /**
     * Build GET PROCESSING OPTIONS command
     * This initiates the transaction and returns Application Interchange Profile (AIP)
     * and Application File Locator (AFL)
     */
    fun buildGpoCommand(pdol: ByteArray? = null): ByteArray {
        val data = if (pdol != null && pdol.isNotEmpty()) {
            // Build PDOL data with proper TLV encoding
            byteArrayOf(0x83.toByte(), pdol.size.toByte(), *pdol)
        } else {
            // Empty PDOL - send 83 00 (tag 83, length 0)
            byteArrayOf(0x83.toByte(), 0x00)
        }
        
        return byteArrayOf(
            0x80.toByte(),          // CLA: Proprietary class
            0xA8.toByte(),          // INS: GET PROCESSING OPTIONS
            0x00,                   // P1
            0x00,                   // P2
            data.size.toByte(),     // Lc
            *data,                  // Command data
            0x00                    // Le
        )
    }

    /**
     * Build READ RECORD command
     * Used to read data from card files specified by AFL
     */
    fun buildReadRecordCommand(recordNumber: Int, sfi: Int): ByteArray {
        return byteArrayOf(
            0x00,                           // CLA
            0xB2.toByte(),                  // INS: READ RECORD
            recordNumber.toByte(),          // P1: Record number
            ((sfi shl 3) or 0x04).toByte(), // P2: SFI with bit 3 set
            0x00                            // Le: Expected length
        )
    }

    /**
     * Check if response indicates success (SW1=90, SW2=00)
     */
    fun isSuccessResponse(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return response[response.size - 2] == 0x90.toByte() && 
               response[response.size - 1] == 0x00.toByte()
    }

    /**
     * Get response data without status words
     */
    fun getResponseData(response: ByteArray): ByteArray {
        return if (response.size > 2) {
            response.copyOfRange(0, response.size - 2)
        } else {
            byteArrayOf()
        }
    }

    /**
     * Convert byte array to hex string for debugging
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /**
     * Convert hex string to byte array
     */
    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").uppercase()
        return ByteArray(cleanHex.length / 2) { i ->
            ((Character.digit(cleanHex[i * 2], 16) shl 4) +
                    Character.digit(cleanHex[i * 2 + 1], 16)).toByte()
        }
    }
}

