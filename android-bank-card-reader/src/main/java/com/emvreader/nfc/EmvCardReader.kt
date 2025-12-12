package com.emvreader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Calendar

/**
 * EMV NFC Card Reader for contactless payment cards.
 * 
 * Supports reading PAN (Primary Account Number) from:
 * - Visa (including qVSDC)
 * - Mastercard
 * - American Express
 * - Discover
 * - UnionPay
 * - JCB
 * - Mir
 * 
 * Usage:
 * ```kotlin
 * val reader = EmvCardReader()
 * val result = reader.readCard(tag)
 * when (result) {
 *     is CardData.Success -> println("Card: ${result.formattedPan}")
 *     is CardData.Error -> println("Error: ${result.message}")
 * }
 * ```
 */
class EmvCardReader(
    private val config: ReaderConfig = ReaderConfig()
) {
    companion object {
        private const val TAG = "EmvCardReader"
    }

    /**
     * Read card data from NFC tag
     * @param tag The NFC tag detected by the system
     * @return CardData with PAN information or error details
     */
    suspend fun readCard(tag: Tag): CardData = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag)
        
        if (isoDep == null) {
            Log.e(TAG, "IsoDep not supported on this card")
            return@withContext CardData.Error(
                code = ErrorCode.UNSUPPORTED_CARD,
                message = "This card type is not supported for contactless reading"
            )
        }

        try {
            isoDep.timeout = config.timeoutMs
            isoDep.connect()
            Log.d(TAG, "Connected to card, max transceive: ${isoDep.maxTransceiveLength}")

            // Step 1: Select PPSE
            val ppseResponse = selectPpse(isoDep) ?: run {
                Log.e(TAG, "PPSE selection failed")
                return@withContext CardData.Error(
                    code = ErrorCode.PPSE_NOT_FOUND,
                    message = "Card does not support contactless reading"
                )
            }

            // Step 2: Parse PPSE and extract AIDs
            val ppseData = TlvParser.parse(ppseResponse)
            val aids = extractAidsFromPpse(ppseData).ifEmpty { EmvConstants.KNOWN_AIDS }
            
            // Step 3: Try each AID
            for (aid in aids) {
                val result = tryReadWithAid(isoDep, aid)
                if (result is CardData.Success) {
                    return@withContext result
                }
            }

            Log.e(TAG, "Could not read PAN from any application")
            return@withContext CardData.Error(
                code = ErrorCode.PAN_NOT_FOUND,
                message = "Could not read card number"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Card read error", e)
            return@withContext when {
                e.message?.contains("Tag was lost") == true -> CardData.Error(
                    code = ErrorCode.TAG_LOST,
                    message = "Card was removed too quickly. Please hold steady."
                )
                e.message?.contains("Transceive failed") == true -> CardData.Error(
                    code = ErrorCode.COMMUNICATION_ERROR,
                    message = "Communication error. Please try again."
                )
                else -> CardData.Error(
                    code = ErrorCode.UNKNOWN,
                    message = e.message ?: "An unexpected error occurred"
                )
            }
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun selectPpse(isoDep: IsoDep): ByteArray? {
        val command = ApduBuilder.selectPpse()
        Log.d(TAG, "SELECT PPSE: ${command.toHex()}")
        
        val response = isoDep.transceive(command)
        Log.d(TAG, "PPSE Response: ${response.toHex()}")

        return if (response.isSuccess()) response.getData() else null
    }

    private fun extractAidsFromPpse(tlvData: Map<String, TlvParser.TlvData>): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()
        tlvData[TlvParser.TAG_AID]?.let { aids.add(it.value) }
        tlvData[TlvParser.TAG_DF_NAME]?.let { aids.add(it.value) }
        return aids
    }

    private fun tryReadWithAid(isoDep: IsoDep, aid: ByteArray): CardData {
        Log.d(TAG, "Trying AID: ${aid.toHex()}")

        // SELECT application
        val selectResponse = isoDep.transceive(ApduBuilder.select(aid))
        Log.d(TAG, "SELECT Response: ${selectResponse.toHex()}")

        if (!selectResponse.isSuccess()) {
            return CardData.Error(ErrorCode.AID_NOT_FOUND, "Application not found")
        }

        val selectData = TlvParser.parse(selectResponse.getData())

        // Check for PAN in SELECT response
        TlvParser.extractPan(selectData)?.let { pan ->
            if (pan.isValidPan()) return createSuccess(pan)
        }

        // GET PROCESSING OPTIONS with various TTQ values
        val pdol = TlvParser.extractPdol(selectData)
        var gpoResponse = tryGpoWithVariants(isoDep, pdol)
        Log.d(TAG, "GPO Response: ${gpoResponse.toHex()}")

        if (!gpoResponse.isSuccess()) {
            // Try empty PDOL
            Log.d(TAG, "GPO failed, trying empty PDOL")
            val emptyGpo = isoDep.transceive(ApduBuilder.gpo(null))
            
            if (!emptyGpo.isSuccess()) {
                // Fallback: direct record read
                Log.d(TAG, "GPO failed, attempting direct record read")
                return tryDirectRecordRead(isoDep)
            }
            gpoResponse = emptyGpo
        }

        val gpoData = TlvParser.parse(gpoResponse.getData())

        // Check for PAN in GPO response
        TlvParser.extractPan(gpoData)?.let { pan ->
            if (pan.isValidPan()) return createSuccess(pan)
        }

        // Read records from AFL
        val aflEntries = TlvParser.extractAfl(gpoData)
        Log.d(TAG, "AFL entries: ${aflEntries.size}")

        for (entry in aflEntries) {
            for (record in entry.firstRecord..entry.lastRecord) {
                try {
                    val readResponse = isoDep.transceive(
                        ApduBuilder.readRecord(record, entry.sfi)
                    )
                    
                    if (readResponse.isSuccess()) {
                        val recordData = TlvParser.parse(readResponse.getData())
                        TlvParser.extractPan(recordData)?.let { pan ->
                            if (pan.isValidPan()) {
                                Log.d(TAG, "PAN found in SFI ${entry.sfi}, Record $record")
                                return createSuccess(pan)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading record ${entry.sfi}/$record", e)
                }
            }
        }

        return CardData.Error(ErrorCode.PAN_NOT_FOUND, "Could not read card number")
    }

    private fun tryGpoWithVariants(isoDep: IsoDep, pdol: ByteArray?): ByteArray {
        for ((index, ttq) in EmvConstants.TTQ_VARIANTS.withIndex()) {
            val pdolData = buildPdolData(pdol, ttq)
            val command = ApduBuilder.gpo(pdolData)
            Log.d(TAG, "GPO Command (variant $index): ${command.toHex()}")
            
            try {
                val response = isoDep.transceive(command)
                Log.d(TAG, "GPO Response (variant $index): ${response.toHex()}")
                
                if (response.isSuccess()) return response
                
                val sw = response.getStatusWord()
                if (sw != 0x6985 && sw != 0x6984 && sw != 0x6A81) {
                    return response
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPO attempt $index failed", e)
            }
        }
        
        return isoDep.transceive(ApduBuilder.gpo(buildPdolData(pdol, EmvConstants.TTQ_VARIANTS[0])))
    }

    private fun buildPdolData(pdol: ByteArray?, ttq: ByteArray): ByteArray? {
        if (pdol == null || pdol.isEmpty()) return null

        val data = mutableListOf<Byte>()
        var offset = 0

        while (offset < pdol.size) {
            val (tag, newOffset) = parsePdolTag(pdol, offset)
            offset = newOffset
            if (offset >= pdol.size) break

            val length = pdol[offset++].toInt() and 0xFF
            val tagData = if (tag == "9F66") ttq.copyOf(length) else getTerminalData(tag, length)
            data.addAll(tagData.toList())
        }

        return data.toByteArray()
    }

    private fun parsePdolTag(data: ByteArray, startOffset: Int): Pair<String, Int> {
        var offset = startOffset
        val tagBytes = mutableListOf<Byte>()
        val firstByte = data[offset++]
        tagBytes.add(firstByte)
        
        if ((firstByte.toInt() and 0x1F) == 0x1F) {
            while (offset < data.size && (data[offset].toInt() and 0x80) != 0) {
                tagBytes.add(data[offset++])
            }
            if (offset < data.size) tagBytes.add(data[offset++])
        }
        
        return Pair(tagBytes.toHex(), offset)
    }

    private fun getTerminalData(tag: String, length: Int): ByteArray = when (tag) {
        "9F66" -> byteArrayOf(0x27, 0x00, 0x00, 0x00).copyOf(length)
        "9F02" -> ByteArray(length).also { if (it.isNotEmpty()) it[it.size - 1] = 0x01 }
        "9F03" -> ByteArray(length)
        "9F1A" -> byteArrayOf(0x08, 0x40).copyOf(length)
        "5F2A" -> byteArrayOf(0x08, 0x40).copyOf(length)
        "9A" -> {
            val cal = Calendar.getInstance()
            val yy = cal.get(Calendar.YEAR) % 100
            val mm = cal.get(Calendar.MONTH) + 1
            val dd = cal.get(Calendar.DAY_OF_MONTH)
            byteArrayOf(
                ((yy / 10 shl 4) or (yy % 10)).toByte(),
                ((mm / 10 shl 4) or (mm % 10)).toByte(),
                ((dd / 10 shl 4) or (dd % 10)).toByte()
            ).copyOf(length)
        }
        "9C" -> byteArrayOf(0x00).copyOf(length)
        "9F37" -> ByteArray(length).also { SecureRandom().nextBytes(it) }
        "95" -> ByteArray(length)
        "9F33" -> byteArrayOf(0xE0.toByte(), 0xF0.toByte(), 0xC8.toByte()).copyOf(length)
        "9F40" -> ByteArray(5).copyOf(length)
        "9F35" -> byteArrayOf(0x22).copyOf(length)
        "9F39" -> byteArrayOf(0x07).copyOf(length)
        else -> ByteArray(length)
    }

    private fun tryDirectRecordRead(isoDep: IsoDep): CardData {
        Log.d(TAG, "Attempting direct record read")
        
        val locations = listOf(
            1 to (1..4), 2 to (1..4), 3 to (1..2), 4 to (1..2)
        )
        
        for ((sfi, range) in locations) {
            for (record in range) {
                try {
                    val response = isoDep.transceive(ApduBuilder.readRecord(record, sfi))
                    if (response.isSuccess()) {
                        TlvParser.extractPan(TlvParser.parse(response.getData()))?.let { pan ->
                            if (pan.isValidPan()) {
                                Log.d(TAG, "PAN found via direct read: SFI $sfi, Record $record")
                                return createSuccess(pan)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        
        return CardData.Error(ErrorCode.PAN_NOT_FOUND, "Could not read card number")
    }

    private fun createSuccess(pan: String): CardData.Success {
        val cardType = CardType.detect(pan)
        return CardData.Success(
            pan = pan,
            formattedPan = pan.formatPan(),
            maskedPan = pan.maskPan(),
            cardType = cardType
        )
    }
}

/**
 * Reader configuration options
 */
data class ReaderConfig(
    val timeoutMs: Int = 5000
)

// Extension functions
private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
private fun List<Byte>.toHex(): String = joinToString("") { "%02X".format(it) }
private fun ByteArray.isSuccess(): Boolean = size >= 2 && this[size - 2] == 0x90.toByte() && this[size - 1] == 0x00.toByte()
private fun ByteArray.getData(): ByteArray = if (size > 2) copyOfRange(0, size - 2) else byteArrayOf()
private fun ByteArray.getStatusWord(): Int = if (size >= 2) ((this[size - 2].toInt() and 0xFF) shl 8) or (this[size - 1].toInt() and 0xFF) else 0
private fun String.isValidPan(): Boolean = length in 13..19 && all { it.isDigit() } && luhnCheck()
private fun String.luhnCheck(): Boolean {
    var sum = 0
    var alternate = false
    for (i in length - 1 downTo 0) {
        var n = this[i] - '0'
        if (alternate) {
            n *= 2
            if (n > 9) n -= 9
        }
        sum += n
        alternate = !alternate
    }
    return sum % 10 == 0
}
private fun String.formatPan(): String = chunked(4).joinToString(" ")
private fun String.maskPan(): String = if (length >= 8) "${take(4)} **** **** ${takeLast(4)}" else this

