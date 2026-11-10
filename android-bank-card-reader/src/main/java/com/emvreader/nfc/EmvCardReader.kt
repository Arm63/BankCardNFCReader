package com.emvreader.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
 * - ArCa (Armenia; brand from PAN prefix 9051 — these cards often run a Mir application)
 *
 * Also detects payment source (physical card vs digital wallets):
 * - Physical cards (traditional plastic EMV cards)
 * - Google Wallet / Google Pay
 * - Samsung Pay
 * - Apple Pay
 * - Other digital wallets
 *
 * One instance can be shared: every [readCard] call keeps its own state, so overlapping
 * reads (e.g. two quick taps) never mix data.
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

        /** Terminal Transaction Qualifiers — the only PDOL entry the TTQ variants change. */
        const val TAG_TTQ = "9F66"

        /** GPO status words after which another TTQ variant is worth trying. */
        val GPO_RETRY_STATUS_WORDS = setOf(0x6985, 0x6984, 0x6A81)

        /** READ RECORD status words meaning "nothing more in this SFI". */
        val PROBE_STOP_STATUS_WORDS = setOf(0x6A83, 0x6A82, 0x6982)

        /** SFI → records scanned when GPO fails and there is no AFL to follow. */
        val DIRECT_READ_LOCATIONS = listOf(1 to 1..4, 2 to 1..4, 3 to 1..2, 4 to 1..2)
    }

    /**
     * Read card data from NFC tag
     * @param tag The NFC tag detected by the system
     * @return CardData with PAN information, payment source detection, or error details
     */
    suspend fun readCard(tag: Tag): CardData = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag)
            ?: return@withContext CardData.Error(
                code = ErrorCode.UNSUPPORTED_CARD,
                message = "This card type is not supported for contactless reading"
            )

        try {
            isoDep.timeout = config.timeoutMs
            isoDep.connect()
            read(ApduTransport { isoDep.transceive(it) })
        } catch (e: Exception) {
            mapException(e)
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /** Runs the EMV read over [transport]. Transport failures are mapped to [CardData.Error]. */
    internal fun read(transport: ApduTransport): CardData =
        try {
            ReadSession(transport).run()
        } catch (e: Exception) {
            mapException(e)
        }

    private fun mapException(e: Exception): CardData.Error = when {
        e is TagLostException || e.message?.contains("Tag was lost") == true -> CardData.Error(
            code = ErrorCode.TAG_LOST,
            message = "Card was removed too quickly. Please hold steady."
        )
        e is IOException -> CardData.Error(
            code = ErrorCode.COMMUNICATION_ERROR,
            message = "Communication error. Please try again."
        )
        else -> CardData.Error(
            code = ErrorCode.UNKNOWN,
            message = e.message ?: "An unexpected error occurred"
        )
    }

    /**
     * Data gathered while reading one application. A fresh context is used per AID, so tags
     * from an application that yielded no PAN never leak into the result of the next one.
     */
    private class AppContext(directoryEntry: TlvParser.PpseApplication) {
        val tlv = mutableMapOf<String, TlvParser.TlvData>()
        var aidHex: String = directoryEntry.aidHex
        var pan: String? = null

        init {
            val aid = directoryEntry.aidHex.hexToBytes()
            tlv[TlvParser.TAG_AID] = TlvParser.TlvData(TlvParser.TAG_AID, aid.size, aid)
            directoryEntry.label?.let { label ->
                val bytes = label.toByteArray(Charsets.ISO_8859_1)
                tlv[TlvParser.TAG_APPLICATION_LABEL] =
                    TlvParser.TlvData(TlvParser.TAG_APPLICATION_LABEL, bytes.size, bytes)
            }
        }

        fun merge(data: Map<String, TlvParser.TlvData>) {
            tlv.putAll(data)
            if (pan == null) pan = TlvParser.extractPan(data)?.takeIf { it.isValidPan() }
        }
    }

    /** All state for one physical read. Never shared between reads. */
    private inner class ReadSession(private val transport: ApduTransport) {

        /** First transport failure. Once set the card is gone and no further APDU is sent. */
        private var linkFailure: Exception? = null
        private var anyAppSelected = false

        fun run(): CardData {
            emvLog("readCard: start")
            val ppse = transmit(ApduBuilder.selectPpse())
            val directory =
                if (ppse.isSuccess()) TlvParser.parsePpseApplications(ppse.getData()) else emptyList()
            emvLog(
                "PPSE ok=${ppse.isSuccess()} apps=" +
                    directory.joinToString(",") { "${it.aidHex}(prio=${it.priority ?: "-"})" }
            )
            val candidates = directory.ifEmpty {
                EmvConstants.KNOWN_AIDS.map { TlvParser.PpseApplication(it.toHex(), label = null, priority = null) }
            }

            for (app in candidates) {
                val result = readApp(app) ?: continue
                emvLog(
                    "readCard: success last4=${result.pan.takeLast(4)} aid=${result.aid} " +
                        "pinTries=${result.pinTriesRemaining ?: "null"} " +
                        "expiry=${result.expiryDate?.let { "${it.displayMmYy()} expired=${it.isExpired()}" } ?: "null"} " +
                        "cardholderName=${result.cardholderName?.let { "\"$it\"" } ?: "null"}"
                )
                return result
            }

            emvLog("readCard: no success for any AID")
            return when {
                anyAppSelected -> CardData.Error(ErrorCode.PAN_NOT_FOUND, "Could not read card number")
                !ppse.isSuccess() -> CardData.Error(
                    ErrorCode.PPSE_NOT_FOUND, "Card does not support contactless reading"
                )
                else -> CardData.Error(ErrorCode.AID_NOT_FOUND, "No supported payment application found on card")
            }
        }

        /** Reads one application. Null when it can't be selected or yields no PAN. */
        private fun readApp(app: TlvParser.PpseApplication): CardData.Success? {
            emvLog("try AID=${app.aidHex}")
            val selectCmd = ApduBuilder.select(app.aidHex.hexToBytes())
            val selectResponse = transmit(selectCmd)
            logApduResult("SELECT", selectCmd, selectResponse)
            if (!selectResponse.isSuccess()) return null
            anyAppSelected = true

            val ctx = AppContext(app)
            try {
                readAppData(ctx, TlvParser.parse(selectResponse.getData()))
            } catch (e: Exception) {
                // Card left mid-read: keep the partial result if the PAN is already known.
                if (linkFailure == null || ctx.pan == null) throw e
                emvLog("link lost after PAN was read — returning partial data")
            }

            val pan = ctx.pan
            if (pan == null) {
                linkFailure?.let { throw it }
                emvLog("AID=${app.aidHex}: PAN_NOT_FOUND")
                return null
            }
            return createSuccess(pan, ctx)
        }

        private fun readAppData(ctx: AppContext, selectData: Map<String, TlvParser.TlvData>) {
            ctx.merge(selectData)
            selectData[TlvParser.TAG_DF_NAME]?.value?.takeIf { it.isNotEmpty() }?.let { ctx.aidHex = it.toHex() }
            logTlv("after_SELECT", ctx.tlv)

            val gpoResponse = getProcessingOptions(TlvParser.extractPdol(selectData))
            val aflSfis: Set<Int>?
            if (gpoResponse == null) {
                emvLog("GPO failed -> direct record read")
                readRecordsDirectly(ctx)
                aflSfis = null
            } else {
                val gpoData = TlvParser.parse(gpoResponse.getData())
                ctx.merge(gpoData)
                logTlv("after_GPO", ctx.tlv)
                val afl = TlvParser.extractAfl(gpoData)
                readAflRecords(ctx, afl)
                aflSfis = afl.map { it.sfi }.toSet()
            }
            logTlv("after_records", ctx.tlv)

            enhance(ctx, aflSfis)
        }

        /**
         * GPO with each TTQ variant (only when the PDOL actually asks for TTQ), then with an
         * empty PDOL if that is a different command. Null if every attempt fails.
         */
        private fun getProcessingOptions(pdol: ByteArray?): ByteArray? {
            val pdolEntries = parsePdol(pdol)
            val ttqVariants =
                if (pdolEntries.any { it.first == TAG_TTQ }) EmvConstants.TTQ_VARIANTS
                else EmvConstants.TTQ_VARIANTS.take(1)

            for (ttq in ttqVariants) {
                val cmd = ApduBuilder.gpo(buildPdolData(pdolEntries, ttq))
                val response = transmit(cmd)
                logApduResult("GPO", cmd, response)
                if (response.isSuccess()) return response
                if (response.getStatusWord() !in GPO_RETRY_STATUS_WORDS) break
            }

            if (pdolEntries.isNotEmpty()) {
                val cmd = ApduBuilder.gpo(null)
                val response = transmit(cmd)
                logApduResult("GPO(empty)", cmd, response)
                if (response.isSuccess()) return response
            }
            return null
        }

        private fun readAflRecords(ctx: AppContext, afl: List<TlvParser.AflEntry>) {
            emvLog("AFL entries=${afl.size} ${afl.joinToString(";") { "SFI=${it.sfi} r${it.firstRecord}-${it.lastRecord}" }}")
            for (entry in afl) {
                for (record in entry.firstRecord..entry.lastRecord) {
                    val response = transmitOrNull(ApduBuilder.readRecord(record, entry.sfi)) ?: return
                    if (response.isSuccess()) {
                        ctx.merge(TlvParser.parse(response.getData()))
                        emvLog("READ_RECORD SFI=${entry.sfi} record=$record ok")
                    } else {
                        emvLog("READ_RECORD SFI=${entry.sfi} record=$record SW=${"%04X".format(response.getStatusWord())}")
                    }
                }
            }
        }

        private fun readRecordsDirectly(ctx: AppContext) {
            for ((sfi, records) in DIRECT_READ_LOCATIONS) {
                for (record in records) {
                    val response = transmitOrNull(ApduBuilder.readRecord(record, sfi)) ?: return
                    if (response.isSuccess()) {
                        ctx.merge(TlvParser.parse(response.getData()))
                        emvLog("direct READ_RECORD SFI=$sfi record=$record ok")
                    }
                }
            }
        }

        /**
         * Optional extras (cardholder name, PIN tries, expiry) that cost more APDUs.
         * Skipped when there is no PAN to decorate, when the link is gone, and for wallets:
         * Token Requestor ID (9F19) is wallet-only, and wallets tear down their HCE session
         * after the AFL exchange, so further APDUs would only stall until timeout.
         */
        private fun enhance(ctx: AppContext, aflSfis: Set<Int>?) {
            if (ctx.pan == null || linkFailure != null) return
            if (ctx.tlv.containsKey(TlvParser.TAG_TOKEN_REQUESTOR_ID)) {
                emvLog("wallet detected (9F19 present) — skipping enhancement scan")
                return
            }
            if (aflSfis != null && TlvParser.extractCardholderName(ctx.tlv) == null) {
                probeExtraRecordsForName(ctx, aflSfis)
            }
            getDataIfMissing(ctx, 0x5F, 0x20) { TlvParser.extractCardholderName(it) != null }
            getDataIfMissing(ctx, 0x9F, 0x17) { TlvParser.extractPinTryCounter(it) != null }
            getDataIfMissing(ctx, 0x5F, 0x24) { TlvParser.extractExpiry(it) != null }
            logTlv("after_GET_DATA", ctx.tlv)
        }

        /**
         * Read records from SFIs not already covered by AFL, looking for 5F20.
         * SFI 1 is the most common extra location for cardholder data on Visa.
         */
        private fun probeExtraRecordsForName(ctx: AppContext, aflSfis: Set<Int>) {
            for (sfi in (1..10).filter { it !in aflSfis }) {
                for (record in 1..5) {
                    val response = transmitOrNull(ApduBuilder.readRecord(record, sfi)) ?: return
                    if (response.isSuccess()) {
                        ctx.merge(TlvParser.parse(response.getData()))
                        if (TlvParser.extractCardholderName(ctx.tlv) != null) {
                            emvLog("probe: name found in SFI=$sfi record=$record")
                            return
                        }
                    } else if (response.getStatusWord() in PROBE_STOP_STATUS_WORDS) {
                        break
                    }
                }
            }
        }

        /** GET DATA for one tag (CLA 80, then 00) unless [isPresent] already holds. */
        private fun getDataIfMissing(
            ctx: AppContext,
            tagHigh: Int,
            tagLow: Int,
            isPresent: (Map<String, TlvParser.TlvData>) -> Boolean
        ) {
            if (isPresent(ctx.tlv)) return
            for (cla in intArrayOf(0x80, 0x00)) {
                val cmd = ApduBuilder.getData(tagHigh, tagLow, cla)
                val response = transmitOrNull(cmd) ?: return
                logApduResult("GET_DATA %02X%02X CLA=%02X".format(tagHigh, tagLow, cla), cmd, response)
                if (!response.isSuccess()) continue
                val data = response.getData()
                if (data.isEmpty()) continue
                ctx.merge(TlvParser.parse(data))
                if (isPresent(ctx.tlv)) return
            }
        }

        /** Sends [command]. A transport failure is remembered and rethrown. */
        private fun transmit(command: ByteArray): ByteArray {
            linkFailure?.let { throw it }
            return try {
                transport.transceive(command)
            } catch (e: Exception) {
                linkFailure = e
                throw e
            }
        }

        /** Like [transmit], but returns null on failure — and without sending once the link is gone. */
        private fun transmitOrNull(command: ByteArray): ByteArray? {
            if (linkFailure != null) return null
            return try {
                transmit(command)
            } catch (e: Exception) {
                emvLog("transport failed: ${e.message} — no further APDUs will be sent")
                null
            }
        }
    }

    private fun createSuccess(pan: String, ctx: AppContext): CardData.Success {
        val cardType = CardType.detect(pan).takeUnless { it == CardType.UNKNOWN }
            ?: CardType.fromAid(ctx.aidHex)

        // Detect payment source (physical card vs digital wallet)
        val detectionResult = CardSourceDetector.detect(ctx.tlv)

        return CardData.Success(
            pan = pan,
            formattedPan = pan.formatPan(),
            maskedPan = pan.maskPan(),
            cardType = cardType,
            paymentSource = detectionResult.source,
            sourceDetectionResult = detectionResult,
            cardholderName = TlvParser.extractCardholderName(ctx.tlv),
            aid = ctx.aidHex,
            pinTriesRemaining = TlvParser.extractPinTryCounter(ctx.tlv),
            expiryDate = TlvParser.extractExpiry(ctx.tlv)
        )
    }

    /** PDOL as (tag, requested length) pairs, in order. */
    private fun parsePdol(pdol: ByteArray?): List<Pair<String, Int>> {
        if (pdol == null) return emptyList()
        val entries = mutableListOf<Pair<String, Int>>()
        var offset = 0
        while (offset < pdol.size) {
            val (tag, afterTag) = parsePdolTag(pdol, offset)
            if (afterTag >= pdol.size) break
            entries += tag to (pdol[afterTag].toInt() and 0xFF)
            offset = afterTag + 1
        }
        return entries
    }

    private fun buildPdolData(entries: List<Pair<String, Int>>, ttq: ByteArray): ByteArray? {
        if (entries.isEmpty()) return null
        val data = mutableListOf<Byte>()
        for ((tag, length) in entries) {
            val value = if (tag == TAG_TTQ) ttq.copyOf(length) else getTerminalData(tag, length)
            data.addAll(value.toList())
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

    private fun emvLog(msg: String) {
        if (!config.debugEmv) return
        Log.d(LOG_TAG, msg)
    }

    /** Log sorted tag keys and the decoded name / expiry, if any. */
    private fun logTlv(phase: String, tlv: Map<String, TlvParser.TlvData>) {
        if (!config.debugEmv) return
        val name = TlvParser.extractCardholderName(tlv)
        val expiry = TlvParser.extractExpiry(tlv)
        emvLog(
            "[$phase] tagCount=${tlv.size} keys=${tlv.keys.sorted().joinToString(",")} " +
                "name=${name?.let { "\"$it\"" } ?: "null"} " +
                "expiry=${expiry?.let { "${it.year}-${"%02d".format(it.month)}" } ?: "null"}"
        )
    }

    private fun logApduResult(label: String, command: ByteArray, response: ByteArray) {
        if (!config.debugEmv) return
        emvLog(
            "$label cmd=${command.toHex().hexPreview(48)} ok=${response.isSuccess()} " +
                "SW=${"%04X".format(response.getStatusWord())} dataLen=${response.getData().size} " +
                "dataHex=${response.getData().toHex().hexPreview()}"
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
private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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
