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
 * Also detects payment source (physical card vs digital wallets):
 * - Physical cards (traditional plastic EMV cards)
 * - Google Wallet / Google Pay
 * - Samsung Pay
 * - Apple Pay
 * - Other digital wallets
 * 
 * Usage:
 * ```kotlin
 * val reader = EmvCardReader()
 * val result = reader.readCard(tag)
 * when (result) {
 *     is CardData.Success -> {
 *         println("Card: ${result.formattedPan}")
 *         println("Source: ${result.paymentSource.displayName}")
 *         println("Is Wallet: ${result.isTokenizedWallet}")
 *     }
 *     is CardData.Error -> println("Error: ${result.message}")
 * }
 * ```
 */
class EmvCardReader(
    private val config: ReaderConfig = ReaderConfig()
) {

    private companion object {
        const val LOG_TAG = "EmvCardReader"
    }
    /**
     * Aggregated TLV data collected during card reading.
     * Used for payment source detection.
     */
    private val collectedTlvData = mutableMapOf<String, TlvParser.TlvData>()

    private fun emvLog(msg: String) {
        if (!config.debugEmv) return
        Log.d(LOG_TAG, msg)
    }

    /** Log sorted tag keys, presence of name-related tags, and decoded name if any. */
    private fun logCollectedTlv(phase: String) {
        if (!config.debugEmv) return
        val keys = collectedTlvData.keys.sorted()
        val extracted = TlvParser.extractCardholderName(collectedTlvData)
        val has5F20 = collectedTlvData.containsKey(TlvParser.TAG_CARDHOLDER_NAME)
        val has56 = collectedTlvData.containsKey(TlvParser.TAG_TRACK1)
        emvLog(
            "[$phase] tagCount=${keys.size} keys=${keys.joinToString(",")} " +
                "has5F20=$has5F20 has56=$has56 extractCardholderName=${extracted?.let { "\"$it\"" } ?: "null"}"
        )
        collectedTlvData[TlvParser.TAG_CARDHOLDER_NAME]?.value?.let { v ->
            emvLog("[$phase] 5F20 raw len=${v.size} hex=${v.toHex().hexPreview()}")
        }
        collectedTlvData[TlvParser.TAG_TRACK1]?.value?.let { v ->
            emvLog("[$phase] 56 Track1 len=${v.size} hex=${v.toHex().hexPreview()}")
        }
    }

    private fun logApduResult(label: String, command: ByteArray, response: ByteArray) {
        if (!config.debugEmv) return
        val sw = response.getStatusWord()
        val ok = response.isSuccess()
        val dataLen = if (response.size >= 2) maxOf(0, response.size - 2) else 0
        emvLog(
            "$label cmd=${command.toHex().hexPreview(48)} ok=$ok SW=${"%04X".format(sw)} dataLen=$dataLen " +
                "dataHex=${response.getData().toHex().hexPreview()}"
        )
    }

    /**
     * Read card data from NFC tag
     * @param tag The NFC tag detected by the system
     * @return CardData with PAN information, payment source detection, or error details
     */
    suspend fun readCard(tag: Tag): CardData = withContext(Dispatchers.IO) {
        // Clear collected TLV data from previous reads
        collectedTlvData.clear()
        
        val isoDep = IsoDep.get(tag)
        
        if (isoDep == null) {
            return@withContext CardData.Error(
                code = ErrorCode.UNSUPPORTED_CARD,
                message = "This card type is not supported for contactless reading"
            )
        }

        try {
            isoDep.timeout = config.timeoutMs
            isoDep.connect()
            emvLog("readCard: start")

            // Step 1: Select PPSE
            val ppseResponse = selectPpse(isoDep) ?: run {
                return@withContext CardData.Error(
                    code = ErrorCode.PPSE_NOT_FOUND,
                    message = "Card does not support contactless reading"
                )
            }

            // Step 2: Parse PPSE and extract AIDs
            val ppseData = TlvParser.parse(ppseResponse)
            collectedTlvData.putAll(ppseData) // Collect for source detection
            val aids = extractAidsFromPpse(ppseData).ifEmpty { EmvConstants.KNOWN_AIDS }
            
            // Step 3: Try each AID
            for (aid in aids) {
                emvLog("try AID=${aid.toHex()}")
                val result = tryReadWithAid(isoDep, aid)
                if (result is CardData.Success) {
                    emvLog(
                        "readCard: success last4=${result.pan.takeLast(4)} " +
                            "aid=${result.aid ?: "null"} " +
                            "pinTries=${result.pinTriesRemaining ?: "null"} " +
                            "cardholderName=${result.cardholderName?.let { "\"$it\"" } ?: "null"}"
                    )
                    return@withContext result
                }
            }
            emvLog("readCard: no success for any AID")

            return@withContext CardData.Error(
                code = ErrorCode.PAN_NOT_FOUND,
                message = "Could not read card number"
            )

        } catch (e: Exception) {
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
        val response = isoDep.transceive(command)
        return if (response.isSuccess()) response.getData() else null
    }

    private fun extractAidsFromPpse(tlvData: Map<String, TlvParser.TlvData>): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()
        tlvData[TlvParser.TAG_AID]?.let { aids.add(it.value) }
        tlvData[TlvParser.TAG_DF_NAME]?.let { aids.add(it.value) }
        return aids
    }

    private fun tryReadWithAid(isoDep: IsoDep, aid: ByteArray): CardData {
        // SELECT application
        val selectCmd = ApduBuilder.select(aid)
        val selectResponse = isoDep.transceive(selectCmd)
        logApduResult("SELECT", selectCmd, selectResponse)

        if (!selectResponse.isSuccess()) {
            emvLog("SELECT failed SW=${"%04X".format(selectResponse.getStatusWord())}")
            return CardData.Error(ErrorCode.AID_NOT_FOUND, "Application not found")
        }

        val selectData = TlvParser.parse(selectResponse.getData())
        collectedTlvData.putAll(selectData) // Collect for source detection
        logCollectedTlv("after_SELECT")

        var foundPan: String? = TlvParser.extractPan(selectData)?.takeIf { it.isValidPan() }
        foundPan?.let { pan ->
            emvLog("PAN in SELECT len=${pan.length} last4=${pan.takeLast(4)} (still reading AFL for name)")
        }

        // GET PROCESSING OPTIONS with various TTQ values
        val pdol = TlvParser.extractPdol(selectData)
        var gpoResponse = tryGpoWithVariants(isoDep, pdol)

        if (!gpoResponse.isSuccess()) {
            emvLog("GPO variants failed SW=${"%04X".format(gpoResponse.getStatusWord())}, try empty PDOL")
            // Try empty PDOL
            val emptyCmd = ApduBuilder.gpo(null)
            val emptyGpo = isoDep.transceive(emptyCmd)
            logApduResult("GPO(empty)", emptyCmd, emptyGpo)

            if (!emptyGpo.isSuccess()) {
                emvLog("GPO empty failed -> tryDirectRecordRead")
                // Fallback: direct record read
                return tryDirectRecordRead(isoDep)
            }
            gpoResponse = emptyGpo
        }

        val gpoData = TlvParser.parse(gpoResponse.getData())
        collectedTlvData.putAll(gpoData) // Collect for source detection
        logCollectedTlv("after_GPO")

        if (foundPan == null) {
            TlvParser.extractPan(gpoData)?.let {
                if (it.isValidPan()) {
                    foundPan = it
                    emvLog("PAN in GPO len=${it.length} last4=${it.takeLast(4)} (still reading AFL for name)")
                }
            }
        }

        // Read all AFL records so tag 5F20 (cardholder) in a later record is merged before success
        val aflEntries = TlvParser.extractAfl(gpoData)
        emvLog("AFL entries=${aflEntries.size} ${
            aflEntries.joinToString(";") { "SFI=${it.sfi} r${it.firstRecord}-${it.lastRecord}" }
        }")
        if (aflEntries.isEmpty()) {
            emvLog("AFL empty — name may only come from GET DATA 5F20")
        }

        for (entry in aflEntries) {
            for (record in entry.firstRecord..entry.lastRecord) {
                try {
                    val readCmd = ApduBuilder.readRecord(record, entry.sfi)
                    val readResponse = isoDep.transceive(readCmd)

                    if (readResponse.isSuccess()) {
                        val recordData = TlvParser.parse(readResponse.getData())
                        val newTags = recordData.keys.filter { it !in collectedTlvData.keys }.sorted()
                        collectedTlvData.putAll(recordData)
                        emvLog(
                            "READ_RECORD SFI=${entry.sfi} record=$record ok=true " +
                                "newTags=${newTags.joinToString(",")} " +
                                "has5F20InRecord=${recordData.containsKey(TlvParser.TAG_CARDHOLDER_NAME)}"
                        )
                        recordData[TlvParser.TAG_CARDHOLDER_NAME]?.value?.let { v ->
                            emvLog("  -> 5F20 in this record hex=${v.toHex().hexPreview()}")
                        }
                        if (foundPan == null) {
                            TlvParser.extractPan(recordData)?.let { pan ->
                                if (pan.isValidPan()) foundPan = pan
                            }
                        }
                    } else {
                        emvLog(
                            "READ_RECORD SFI=${entry.sfi} record=$record ok=false SW=${
                                "%04X".format(readResponse.getStatusWord())
                            }"
                        )
                    }
                } catch (e: Exception) {
                    emvLog("READ_RECORD SFI=${entry.sfi} record=$record exception=${e.message}")
                }
            }
        }

        logCollectedTlv("after_AFL")

        // If cardholder name still not found, probe SFIs not in AFL (SFI 1 commonly
        // holds cardholder data that issuers omit from contactless AFL for privacy).
        if (TlvParser.extractCardholderName(collectedTlvData) == null) {
            val aflSfis = aflEntries.map { it.sfi }.toSet()
            probeExtraRecordsForName(isoDep, aflSfis)
        }
        logCollectedTlv("after_extra_SFI_probe")

        mergeCardholderFromGetData(isoDep)
        mergePinTryCounterFromGetData(isoDep)
        logCollectedTlv("after_GET_DATA_5F20_9F17")

        foundPan?.let {
            val name = TlvParser.extractCardholderName(collectedTlvData)
            emvLog("tryReadWithAid success last4=${it.takeLast(4)} finalExtractedName=${name?.let { n -> "\"$n\"" } ?: "null"}")
            return createSuccess(it)
        }

        emvLog("tryReadWithAid: PAN_NOT_FOUND after AFL+GET DATA")
        return CardData.Error(ErrorCode.PAN_NOT_FOUND, "Could not read card number")
    }

    /**
     * Read records from SFIs not already covered by AFL, looking for 5F20.
     * SFI 1 is the most common extra location for cardholder data on Visa.
     */
    private fun probeExtraRecordsForName(isoDep: IsoDep, aflSfis: Set<Int>) {
        val probeSfis = (1..10).filter { it !in aflSfis }
        emvLog("probeExtraRecords SFIs=$probeSfis (AFL covered $aflSfis)")
        for (sfi in probeSfis) {
            for (record in 1..5) {
                try {
                    val readCmd = ApduBuilder.readRecord(record, sfi)
                    val response = isoDep.transceive(readCmd)
                    if (response.isSuccess()) {
                        val recordData = TlvParser.parse(response.getData())
                        val newTags = recordData.keys.filter { it !in collectedTlvData.keys }.sorted()
                        collectedTlvData.putAll(recordData)
                        emvLog(
                            "probe READ_RECORD SFI=$sfi record=$record ok=true " +
                                "newTags=${newTags.joinToString(",")} " +
                                "has5F20=${recordData.containsKey(TlvParser.TAG_CARDHOLDER_NAME)}"
                        )
                        recordData[TlvParser.TAG_CARDHOLDER_NAME]?.value?.let { v ->
                            emvLog("  -> 5F20 in probe hex=${v.toHex().hexPreview()}")
                        }
                        if (TlvParser.extractCardholderName(collectedTlvData) != null) {
                            emvLog("probeExtraRecords: name found in SFI=$sfi record=$record")
                            return
                        }
                    } else {
                        val sw = response.getStatusWord()
                        emvLog("probe READ_RECORD SFI=$sfi record=$record SW=${"%04X".format(sw)}")
                        if (sw == 0x6A83 || sw == 0x6A82 || sw == 0x6982) break
                    }
                } catch (e: Exception) {
                    emvLog("probe READ_RECORD SFI=$sfi record=$record ex=${e.message}")
                    break
                }
            }
        }
        emvLog("probeExtraRecords: name still null after probing")
    }

    /** Try GET DATA for tag 5F20 when AFL did not yield the cardholder name. */
    private fun mergeCardholderFromGetData(isoDep: IsoDep) {
        if (TlvParser.extractCardholderName(collectedTlvData) != null) {
            emvLog("GET_DATA 5F20: skip (name already in TLV map)")
            return
        }
        for (cla in intArrayOf(0x80, 0x00)) {
            try {
                val cmd = ApduBuilder.getData(0x5F, 0x20, cla)
                val resp = isoDep.transceive(cmd)
                logApduResult("GET_DATA 5F20 CLA=${"%02X".format(cla)}", cmd, resp)
                if (!resp.isSuccess()) {
                    emvLog("GET_DATA 5F20 CLA=${"%02X".format(cla)} failed SW=${"%04X".format(resp.getStatusWord())}")
                    continue
                }
                val data = resp.getData()
                if (data.isEmpty()) {
                    emvLog("GET_DATA 5F20 CLA=${"%02X".format(cla)} empty body")
                    continue
                }
                val parsed = TlvParser.parse(data)
                emvLog("GET_DATA 5F20 parsed tags=${parsed.keys.sorted().joinToString(",")}")
                collectedTlvData.putAll(parsed)
                if (TlvParser.extractCardholderName(collectedTlvData) != null) {
                    emvLog("GET_DATA 5F20: extractCardholderName now ok")
                    return
                }
            } catch (e: Exception) {
                emvLog("GET_DATA 5F20 CLA=${"%02X".format(cla)} exception=${e.message}")
            }
        }
        emvLog("GET_DATA 5F20: exhausted; name still null")
    }

    /** GET DATA for tag 9F17 (PIN try counter); merges into [collectedTlvData] on success. */
    private fun mergePinTryCounterFromGetData(isoDep: IsoDep) {
        if (TlvParser.extractPinTryCounter(collectedTlvData) != null) {
            emvLog("GET_DATA 9F17: skip (already in TLV map)")
            return
        }
        for (cla in intArrayOf(0x80, 0x00)) {
            try {
                val cmd = ApduBuilder.getData(0x9F, 0x17, cla)
                val resp = isoDep.transceive(cmd)
                logApduResult("GET_DATA 9F17 CLA=${"%02X".format(cla)}", cmd, resp)
                if (!resp.isSuccess()) {
                    emvLog("GET_DATA 9F17 CLA=${"%02X".format(cla)} failed SW=${"%04X".format(resp.getStatusWord())}")
                    continue
                }
                val data = resp.getData()
                if (data.isEmpty()) continue
                val parsed = TlvParser.parse(data)
                emvLog("GET_DATA 9F17 parsed tags=${parsed.keys.sorted().joinToString(",")}")
                collectedTlvData.putAll(parsed)
                if (TlvParser.extractPinTryCounter(collectedTlvData) != null) {
                    emvLog("GET_DATA 9F17: pin try counter=${TlvParser.extractPinTryCounter(collectedTlvData)}")
                    return
                }
            } catch (e: Exception) {
                emvLog("GET_DATA 9F17 CLA=${"%02X".format(cla)} exception=${e.message}")
            }
        }
        emvLog("GET_DATA 9F17: exhausted or unsupported")
    }

    private fun tryGpoWithVariants(isoDep: IsoDep, pdol: ByteArray?): ByteArray {
        for ((index, ttq) in EmvConstants.TTQ_VARIANTS.withIndex()) {
            val pdolData = buildPdolData(pdol, ttq)
            val command = ApduBuilder.gpo(pdolData)
            
            try {
                val response = isoDep.transceive(command)
                
                if (response.isSuccess()) return response
                
                val sw = response.getStatusWord()
                if (sw != 0x6985 && sw != 0x6984 && sw != 0x6A81) {
                    return response
                }
            } catch (e: Exception) {
                // Continue with next variant
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
        emvLog("tryDirectRecordRead: start")
        logCollectedTlv("direct_before_scan")
        val locations = listOf(
            1 to (1..4), 2 to (1..4), 3 to (1..2), 4 to (1..2)
        )
        var foundPan: String? = TlvParser.extractPan(collectedTlvData)?.takeIf { it.isValidPan() }
        for ((sfi, range) in locations) {
            for (record in range) {
                try {
                    val readCmd = ApduBuilder.readRecord(record, sfi)
                    val response = isoDep.transceive(readCmd)
                    if (response.isSuccess()) {
                        val recordData = TlvParser.parse(response.getData())
                        val newTags = recordData.keys.filter { it !in collectedTlvData.keys }.sorted()
                        collectedTlvData.putAll(recordData)
                        emvLog(
                            "direct READ_RECORD SFI=$sfi record=$record newTags=${newTags.joinToString(",")} " +
                                "has5F20=${recordData.containsKey(TlvParser.TAG_CARDHOLDER_NAME)}"
                        )
                        if (foundPan == null) {
                            TlvParser.extractPan(recordData)?.let { pan ->
                                if (pan.isValidPan()) foundPan = pan
                            }
                        }
                    } else if (config.debugEmv) {
                        emvLog(
                            "direct READ_RECORD SFI=$sfi record=$record SW=${
                                "%04X".format(response.getStatusWord())
                            }"
                        )
                    }
                } catch (e: Exception) {
                    emvLog("direct READ_RECORD SFI=$sfi record=$record ex=${e.message}")
                }
            }
        }
        logCollectedTlv("direct_after_scan")
        mergeCardholderFromGetData(isoDep)
        mergePinTryCounterFromGetData(isoDep)
        logCollectedTlv("direct_after_GET_DATA")
        foundPan?.let {
            emvLog("tryDirectRecordRead success last4=${it.takeLast(4)} name=${TlvParser.extractCardholderName(collectedTlvData)}")
            return createSuccess(it)
        }
        emvLog("tryDirectRecordRead: PAN_NOT_FOUND")
        return CardData.Error(ErrorCode.PAN_NOT_FOUND, "Could not read card number")
    }

    private fun createSuccess(pan: String): CardData.Success {
        val cardType = CardType.detect(pan)
        
        // Detect payment source (physical card vs digital wallet)
        val detectionResult = CardSourceDetector.detect(collectedTlvData)
        
        return CardData.Success(
            pan = pan,
            formattedPan = pan.formatPan(),
            maskedPan = pan.maskPan(),
            cardType = cardType,
            paymentSource = detectionResult.source,
            sourceDetectionResult = detectionResult,
            cardholderName = TlvParser.extractCardholderName(collectedTlvData),
            aid = TlvParser.extractAid(collectedTlvData),
            pinTriesRemaining = TlvParser.extractPinTryCounter(collectedTlvData)
        )
    }
}

/**
 * Reader configuration options
 */
data class ReaderConfig(
    val timeoutMs: Int = 5000,
    /**
     * When true, emits verbose [android.util.Log] lines with tag `"EmvCardReader"`
     * for debugging cardholder name / TLV collection (Android Studio logcat filter: EmvCardReader).
     */
    val debugEmv: Boolean = false
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

private fun String.hexPreview(maxChars: Int = 160): String =
    if (length <= maxChars) this else take(maxChars) + "…(${length} hex chars)"

