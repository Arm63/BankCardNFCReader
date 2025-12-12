package com.example.bankcardreader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFC Card Reader for EMV contactless payment cards.
 * Handles communication with the card using APDU commands.
 */
class NfcCardReader {

    companion object {
        private const val TAG = "NfcCardReader"
        private const val TIMEOUT_MS = 5000
    }

    /**
     * Read card data from NFC tag
     * Returns CardReadResult with PAN or error
     */
    suspend fun readCard(tag: Tag): CardReadResult = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag)
        
        if (isoDep == null) {
            Log.e(TAG, "IsoDep not supported on this card")
            return@withContext CardReadResult.Error(CardReadError.UNSUPPORTED_CARD)
        }

        try {
            isoDep.timeout = TIMEOUT_MS
            isoDep.connect()
            Log.d(TAG, "Connected to card, max transceive length: ${isoDep.maxTransceiveLength}")

            // Step 1: Select PPSE to get available payment applications
            val ppseResponse = selectPpse(isoDep)
            if (ppseResponse == null) {
                Log.e(TAG, "PPSE selection failed")
                return@withContext CardReadResult.Error(CardReadError.PPSE_NOT_FOUND)
            }

            // Step 2: Parse PPSE response to find AIDs
            val ppseData = TlvParser.parse(ppseResponse)
            val aids = extractAidsFromPpse(ppseData)

            // Step 3: Try each AID or fallback to known AIDs
            val aidsToTry = if (aids.isNotEmpty()) aids else EmvUtils.KNOWN_AIDS
            
            for (aid in aidsToTry) {
                val result = tryReadWithAid(isoDep, aid)
                if (result is CardReadResult.Success) {
                    return@withContext result
                }
            }

            Log.e(TAG, "Could not read PAN from any application")
            return@withContext CardReadResult.Error(CardReadError.PAN_NOT_FOUND)

        } catch (e: Exception) {
            Log.e(TAG, "Card read error", e)
            return@withContext when {
                e.message?.contains("Tag was lost") == true -> 
                    CardReadResult.Error(CardReadError.TAG_LOST)
                e.message?.contains("Transceive failed") == true ->
                    CardReadResult.Error(CardReadError.COMMUNICATION_ERROR)
                else -> 
                    CardReadResult.Error(CardReadError.UNKNOWN_ERROR, e.message)
            }
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing IsoDep", e)
            }
        }
    }

    /**
     * Select PPSE (Proximity Payment System Environment)
     */
    private fun selectPpse(isoDep: IsoDep): ByteArray? {
        val command = EmvUtils.buildSelectPpseCommand()
        Log.d(TAG, "SELECT PPSE: ${EmvUtils.toHexString(command)}")
        
        val response = isoDep.transceive(command)
        Log.d(TAG, "PPSE Response: ${EmvUtils.toHexString(response)}")

        return if (EmvUtils.isSuccessResponse(response)) {
            EmvUtils.getResponseData(response)
        } else {
            null
        }
    }

    /**
     * Extract AIDs from PPSE response
     */
    private fun extractAidsFromPpse(tlvData: Map<String, TlvParser.TlvData>): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()
        
        // Look for DF Name (tag 4F) which contains AID
        tlvData[TlvParser.TAG_AID]?.let { aids.add(it.value) }
        tlvData[TlvParser.TAG_DF_NAME]?.let { aids.add(it.value) }
        
        return aids
    }

    /**
     * Try to read PAN using specific AID
     */
    private fun tryReadWithAid(isoDep: IsoDep, aid: ByteArray): CardReadResult {
        Log.d(TAG, "Trying AID: ${EmvUtils.toHexString(aid)}")

        // Step 1: SELECT application
        val selectCommand = EmvUtils.buildSelectCommand(aid)
        val selectResponse = isoDep.transceive(selectCommand)
        Log.d(TAG, "SELECT Response: ${EmvUtils.toHexString(selectResponse)}")

        if (!EmvUtils.isSuccessResponse(selectResponse)) {
            return CardReadResult.Error(CardReadError.AID_NOT_FOUND)
        }

        val selectData = TlvParser.parse(EmvUtils.getResponseData(selectResponse))

        // Check for PAN in SELECT response (some cards)
        TlvParser.extractPan(selectData)?.let { pan ->
            if (CardValidator.isValidCardNumber(pan)) {
                return createSuccessResult(pan)
            }
        }

        // Step 2: GET PROCESSING OPTIONS
        val pdol = TlvParser.extractPdol(selectData)
        
        // Try GPO with proper PDOL data
        var gpoResponse = tryGpoWithPdol(isoDep, pdol)
        Log.d(TAG, "GPO Response: ${EmvUtils.toHexString(gpoResponse)}")

        var successfulGpoResponse: ByteArray? = null
        
        if (EmvUtils.isSuccessResponse(gpoResponse)) {
            successfulGpoResponse = gpoResponse
        } else {
            // Try with empty PDOL
            Log.d(TAG, "GPO failed with PDOL, trying empty PDOL")
            val gpoCommandEmpty = EmvUtils.buildGpoCommand(null)
            Log.d(TAG, "GPO Command (empty): ${EmvUtils.toHexString(gpoCommandEmpty)}")
            val gpoResponseEmpty = isoDep.transceive(gpoCommandEmpty)
            Log.d(TAG, "GPO Response (empty): ${EmvUtils.toHexString(gpoResponseEmpty)}")
            
            if (EmvUtils.isSuccessResponse(gpoResponseEmpty)) {
                successfulGpoResponse = gpoResponseEmpty
            } else {
                // GPO failed - try reading common record locations directly
                Log.d(TAG, "GPO failed, attempting direct record read")
                val directResult = tryDirectRecordRead(isoDep)
                if (directResult is CardReadResult.Success) {
                    return directResult
                }
            }
        }

        if (successfulGpoResponse == null) {
            return CardReadResult.Error(CardReadError.GPO_FAILED)
        }

        val gpoData = TlvParser.parse(EmvUtils.getResponseData(successfulGpoResponse))

        // Check for PAN in GPO response
        TlvParser.extractPan(gpoData)?.let { pan ->
            if (CardValidator.isValidCardNumber(pan)) {
                return createSuccessResult(pan)
            }
        }

        // Step 3: READ RECORDS based on AFL
        val aflEntries = TlvParser.extractAfl(gpoData)
        Log.d(TAG, "AFL entries: ${aflEntries.size}")

        for (entry in aflEntries) {
            for (record in entry.firstRecord..entry.lastRecord) {
                try {
                    val readCommand = EmvUtils.buildReadRecordCommand(record, entry.sfi)
                    val readResponse = isoDep.transceive(readCommand)
                    
                    if (EmvUtils.isSuccessResponse(readResponse)) {
                        val recordData = TlvParser.parse(EmvUtils.getResponseData(readResponse))
                        
                        TlvParser.extractPan(recordData)?.let { pan ->
                            if (CardValidator.isValidCardNumber(pan)) {
                                Log.d(TAG, "PAN found in SFI ${entry.sfi}, Record $record")
                                return createSuccessResult(pan)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading record ${entry.sfi}/$record", e)
                }
            }
        }

        return CardReadResult.Error(CardReadError.PAN_NOT_FOUND)
    }

    /**
     * Build PDOL data with proper terminal values
     * PDOL specifies what terminal data the card expects
     */
    private fun buildPdolData(pdol: ByteArray?): ByteArray? {
        if (pdol == null || pdol.isEmpty()) return null

        val data = mutableListOf<Byte>()
        var offset = 0

        while (offset < pdol.size) {
            // Parse tag
            val tagResult = parsePdolTag(pdol, offset)
            val tag = tagResult.first
            offset = tagResult.second

            if (offset >= pdol.size) break

            // Parse length
            val length = pdol[offset++].toInt() and 0xFF

            // Provide proper terminal data for known tags
            val tagData = getTerminalDataForTag(tag, length)
            data.addAll(tagData.toList())
        }

        return data.toByteArray()
    }

    /**
     * Get terminal data for known EMV tags
     * Returns proper values instead of zeros to satisfy card requirements
     */
    private fun getTerminalDataForTag(tag: String, length: Int): ByteArray {
        return when (tag) {
            // Terminal Transaction Qualifiers (TTQ) - Visa requires proper flags
            "9F66" -> byteArrayOf(
                0x27.toByte(),  // qVSDC supported, contact chip, mag stripe modes
                0x00,
                0x00,
                0x00
            ).copyOf(length)
            
            // Amount Authorized - use minimal non-zero amount (1 unit)
            "9F02" -> ByteArray(length).also { 
                if (it.isNotEmpty()) it[it.size - 1] = 0x01 
            }
            
            // Amount Other - can be zero
            "9F03" -> ByteArray(length)
            
            // Terminal Country Code - use US (840 = 0x0840)
            "9F1A" -> byteArrayOf(0x08.toByte(), 0x40.toByte()).copyOf(length)
            
            // Transaction Currency Code - USD (840 = 0x0840)
            "5F2A" -> byteArrayOf(0x08.toByte(), 0x40.toByte()).copyOf(length)
            
            // Transaction Date (YYMMDD BCD)
            "9A" -> {
                val cal = java.util.Calendar.getInstance()
                val yy = (cal.get(java.util.Calendar.YEAR) % 100)
                val mm = cal.get(java.util.Calendar.MONTH) + 1
                val dd = cal.get(java.util.Calendar.DAY_OF_MONTH)
                byteArrayOf(
                    ((yy / 10 shl 4) or (yy % 10)).toByte(),
                    ((mm / 10 shl 4) or (mm % 10)).toByte(),
                    ((dd / 10 shl 4) or (dd % 10)).toByte()
                ).copyOf(length)
            }
            
            // Transaction Type (0x00 = goods/services)
            "9C" -> byteArrayOf(0x00).copyOf(length)
            
            // Unpredictable Number - random 4 bytes
            "9F37" -> ByteArray(length).also { 
                java.security.SecureRandom().nextBytes(it) 
            }
            
            // Terminal Verification Results (TVR)
            "95" -> ByteArray(length)
            
            // Terminal Capabilities
            "9F33" -> byteArrayOf(0xE0.toByte(), 0xF0.toByte(), 0xC8.toByte()).copyOf(length)
            
            // Additional Terminal Capabilities
            "9F40" -> byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00).copyOf(length)
            
            // Terminal Type (22 = offline with online capability)
            "9F35" -> byteArrayOf(0x22).copyOf(length)
            
            // Point of Service Entry Mode
            "9F39" -> byteArrayOf(0x07).copyOf(length)
            
            // Merchant Category Code
            "9F15" -> byteArrayOf(0x00, 0x00).copyOf(length)
            
            // Terminal Floor Limit
            "9F1B" -> byteArrayOf(0x00, 0x00, 0x00, 0x00).copyOf(length)
            
            // Default: fill with zeros
            else -> ByteArray(length)
        }
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
            if (offset < data.size) {
                tagBytes.add(data[offset++])
            }
        }
        
        val tagHex = tagBytes.joinToString("") { "%02X".format(it) }
        return Pair(tagHex, offset)
    }

    /**
     * Try GPO with PDOL, using different TTQ values if needed
     */
    private fun tryGpoWithPdol(isoDep: IsoDep, pdol: ByteArray?): ByteArray {
        // Different TTQ values to try (for Visa qVSDC compatibility)
        val ttqVariants = listOf(
            byteArrayOf(0x27.toByte(), 0x00, 0x00, 0x00),  // Standard qVSDC
            byteArrayOf(0xA6.toByte(), 0x00, 0x00, 0x00),  // Online PIN, signature
            byteArrayOf(0xE6.toByte(), 0x00, 0x00, 0x00),  // All methods
            byteArrayOf(0x36.toByte(), 0x00, 0x00, 0x00),  // Contact + contactless
            byteArrayOf(0xB6.toByte(), 0x00, 0x40.toByte(), 0x00),  // Extended
        )
        
        for ((index, ttq) in ttqVariants.withIndex()) {
            val pdolData = buildPdolDataWithTtq(pdol, ttq)
            val gpoCommand = EmvUtils.buildGpoCommand(pdolData)
            Log.d(TAG, "GPO Command (TTQ variant $index): ${EmvUtils.toHexString(gpoCommand)}")
            
            try {
                val response = isoDep.transceive(gpoCommand)
                Log.d(TAG, "GPO Response (TTQ variant $index): ${EmvUtils.toHexString(response)}")
                
                if (EmvUtils.isSuccessResponse(response)) {
                    return response
                }
                
                // If we get 6985 or similar, try next TTQ
                val sw = if (response.size >= 2) {
                    ((response[response.size - 2].toInt() and 0xFF) shl 8) or 
                    (response[response.size - 1].toInt() and 0xFF)
                } else 0
                
                // Only continue trying if it's a recoverable error
                if (sw != 0x6985 && sw != 0x6984 && sw != 0x6A81) {
                    return response
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPO attempt $index failed", e)
            }
        }
        
        // Return last failed response
        val pdolData = buildPdolData(pdol)
        val gpoCommand = EmvUtils.buildGpoCommand(pdolData)
        return isoDep.transceive(gpoCommand)
    }
    
    /**
     * Build PDOL data with specific TTQ value
     */
    private fun buildPdolDataWithTtq(pdol: ByteArray?, ttq: ByteArray): ByteArray? {
        if (pdol == null || pdol.isEmpty()) return null

        val data = mutableListOf<Byte>()
        var offset = 0

        while (offset < pdol.size) {
            val tagResult = parsePdolTag(pdol, offset)
            val tag = tagResult.first
            offset = tagResult.second

            if (offset >= pdol.size) break

            val length = pdol[offset++].toInt() and 0xFF

            // Use custom TTQ for 9F66
            val tagData = if (tag == "9F66") {
                ttq.copyOf(length)
            } else {
                getTerminalDataForTag(tag, length)
            }
            data.addAll(tagData.toList())
        }

        return data.toByteArray()
    }

    /**
     * Try to read records directly from common SFI/record locations
     * Some cards allow reading without a successful GPO
     */
    private fun tryDirectRecordRead(isoDep: IsoDep): CardReadResult {
        Log.d(TAG, "Attempting direct record read from common locations")
        
        // Common SFI values where PAN is typically stored
        val commonLocations = listOf(
            Pair(1, 1..4),   // SFI 1, records 1-4
            Pair(2, 1..4),   // SFI 2, records 1-4
            Pair(3, 1..2),   // SFI 3, records 1-2
            Pair(4, 1..2),   // SFI 4, records 1-2
        )
        
        for ((sfi, recordRange) in commonLocations) {
            for (record in recordRange) {
                try {
                    val readCommand = EmvUtils.buildReadRecordCommand(record, sfi)
                    val readResponse = isoDep.transceive(readCommand)
                    
                    if (EmvUtils.isSuccessResponse(readResponse)) {
                        Log.d(TAG, "Direct read success: SFI $sfi, Record $record")
                        val recordData = TlvParser.parse(EmvUtils.getResponseData(readResponse))
                        
                        TlvParser.extractPan(recordData)?.let { pan ->
                            if (CardValidator.isValidCardNumber(pan)) {
                                Log.d(TAG, "PAN found via direct read: SFI $sfi, Record $record")
                                return createSuccessResult(pan)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next record
                }
            }
        }
        
        return CardReadResult.Error(CardReadError.PAN_NOT_FOUND)
    }

    private fun createSuccessResult(pan: String): CardReadResult.Success {
        val cardType = CardValidator.detectCardType(pan)
        val formattedPan = CardValidator.formatCardNumber(pan)
        return CardReadResult.Success(
            pan = pan,
            formattedPan = formattedPan,
            cardType = cardType
        )
    }
}

/**
 * Result of card reading operation
 */
sealed class CardReadResult {
    data class Success(
        val pan: String,
        val formattedPan: String,
        val cardType: CardValidator.CardType
    ) : CardReadResult()

    data class Error(
        val error: CardReadError,
        val message: String? = null
    ) : CardReadResult()
}

/**
 * Possible card reading errors
 */
enum class CardReadError(val userMessage: String) {
    UNSUPPORTED_CARD("This card type is not supported"),
    PPSE_NOT_FOUND("Card does not support contactless reading"),
    AID_NOT_FOUND("Payment application not found on card"),
    GPO_FAILED("Failed to initialize card communication"),
    PAN_NOT_FOUND("Could not read card number"),
    TAG_LOST("Card was removed too quickly. Please hold steady."),
    COMMUNICATION_ERROR("Communication error. Please try again."),
    UNKNOWN_ERROR("An unexpected error occurred")
}

