package com.example.suicanfcreader.lib

import android.nfc.tech.NfcF
import com.example.suicanfcreader.model.Card
import com.example.suicanfcreader.model.RapicaStopDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Read-only decoder for the RapiCa FeliCa system.
 *
 * This path is intentionally selected only for system 0x8194. Service and record
 * fields follow the public MIT-licensed felica-look RapiCa implementation; unknown
 * record kinds are shown as raw RapiCa events instead of being treated as Suica data.
 * https://github.com/gnue/felica-look/tree/v0.2.0/rapica
 */
object RapicaReader {
    const val SYSTEM_CODE = 0x8194
    const val SERVICE_ATTRIBUTES = 0x004B
    const val SERVICE_HISTORY = 0x008F

    private const val RESPONSE_HEADER_SIZE = 13
    private const val BLOCK_SIZE = 16
    private const val MAX_HISTORY_BLOCKS = 50
    private const val MAX_BLOCKS_PER_REQUEST = 10
    private const val MAX_BALANCE = 1_000_000

    data class ReadResult(
        val cards: List<Card>,
        val rawResponses: List<ByteArray>
    )

    private data class HistoryRecord(
        val raw: ByteArray,
        val timestamp: LocalDateTime,
        val companyCode: Int,
        val stopCode: Int,
        val lineCode: Int,
        val kindCode: Int,
        val balance: Int
    )

    private data class ReadChunk(
        val blocks: List<ByteArray>,
        val raw: ByteArray,
        val requestedBlocks: Int
    )

    @JvmStatic
    fun matchesSystemCode(systemCode: ByteArray?): Boolean =
        systemCode?.size == 2 &&
            u(systemCode[0]) == (SYSTEM_CODE shr 8) &&
            u(systemCode[1]) == (SYSTEM_CODE and 0xff)

    /** Must be called after NfcF.connect(). */
    fun read(
        felica: NfcF,
        idm: ByteArray,
        cardId: String,
        context: android.content.Context
    ): ReadResult? {
        if (idm.size != 8) return null
        val latestTimestamp = readLatestTimestamp(felica, idm)
        val (blocks, rawResponses) = readHistoryBlocks(felica, idm) ?: return null

        var previousTimestamp = latestTimestamp ?: LocalDateTime.now().plusDays(1)
        val records = blocks.mapNotNull { block ->
            val record = parseRecord(block, previousTimestamp) ?: return@mapNotNull null
            previousTimestamp = record.timestamp
            record
        }
        if (records.isEmpty()) return null

        val cards = toCards(records, cardId, context, yearEstimated = latestTimestamp == null)
        return cards.takeIf { it.isNotEmpty() }?.let { ReadResult(it, rawResponses) }
    }

    /** The attribute service stores a full date and lets us infer history years safely. */
    private fun readLatestTimestamp(felica: NfcF, idm: ByteArray): LocalDateTime? {
        val chunk = readChunk(felica, idm, 0, 1, SERVICE_ATTRIBUTES) ?: return null
        val block = chunk.blocks.firstOrNull() ?: return null
        if (block.size != BLOCK_SIZE) return null
        val year = 2000 + u(block[0])
        val month = u(block[1])
        val day = u(block[2])
        val hour = u(block[3])
        val minute = u(block[4])
        val timestamp = runCatching {
            if (year > LocalDateTime.now().year + 1) return null
            if (hour !in 0..23 || minute !in 0..59) return null
            LocalDate.of(year, month, day).atTime(hour, minute)
        }.getOrNull() ?: return null
        return timestamp.takeIf { !it.isAfter(LocalDateTime.now().plusDays(1)) }
    }

    /**
     * Reads in bounded chunks. If a card rejects a large first request because its
     * service is shorter, retry smaller read-only requests; later failures preserve
     * already validated blocks and never switch to another service/parser.
     */
    private fun readHistoryBlocks(felica: NfcF, idm: ByteArray): Pair<List<ByteArray>, List<ByteArray>>? {
        val blocks = mutableListOf<ByteArray>()
        val rawResponses = mutableListOf<ByteArray>()
        var startBlock = 0

        while (startBlock < MAX_HISTORY_BLOCKS) {
            val desired = minOf(MAX_BLOCKS_PER_REQUEST, MAX_HISTORY_BLOCKS - startBlock)
            val chunk = readChunk(felica, idm, startBlock, desired, SERVICE_HISTORY)
                ?: if (startBlock == 0) return null else break

            if (chunk.blocks.isEmpty()) break
            blocks.addAll(chunk.blocks)
            rawResponses.add(chunk.raw)
            startBlock += chunk.blocks.size
            if (chunk.blocks.size < chunk.requestedBlocks) break
        }

        return blocks.takeIf { it.isNotEmpty() }?.let { it to rawResponses }
    }

    private fun readChunk(
        felica: NfcF,
        idm: ByteArray,
        startBlock: Int,
        desired: Int,
        serviceCode: Int
    ): ReadChunk? {
        val requestSizes = generateSequence(desired) { current ->
            if (current <= 1) null else maxOf(1, current / 2)
        }.distinct().toList()

        for (requestSize in requestSizes) {
            val response = runCatching {
                felica.transceive(
                    SuicaReader.readWithoutEncryption(
                        idm = idm,
                        startBlock = startBlock,
                        size = requestSize,
                        serviceCode = serviceCode
                    )
                )
            }.getOrNull() ?: continue

            val parsed = parseHistoryResponse(response, idm, requestSize) ?: continue
            if (parsed.isEmpty()) return ReadChunk(parsed, response, requestSize)
            return ReadChunk(parsed, response, requestSize)
        }
        return null
    }

    @JvmStatic
    fun parseHistoryResponse(response: ByteArray, expectedIdm: ByteArray, requestedBlocks: Int): List<ByteArray>? {
        if (expectedIdm.size != 8 || requestedBlocks !in 1..MAX_BLOCKS_PER_REQUEST) return null
        if (response.size < RESPONSE_HEADER_SIZE || response.size > 255) return null
        if (u(response[0]) != response.size || u(response[1]) != 0x07) return null
        if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) return null
        if (u(response[10]) != 0 || u(response[11]) != 0) return null

        val count = u(response[12])
        if (count > requestedBlocks) return null
        if (response.size != RESPONSE_HEADER_SIZE + count * BLOCK_SIZE) return null
        return List(count) { index ->
            val offset = RESPONSE_HEADER_SIZE + index * BLOCK_SIZE
            response.copyOfRange(offset, offset + BLOCK_SIZE)
        }
    }

    private fun parseRecord(block: ByteArray, notAfter: LocalDateTime): HistoryRecord? {
        if (block.size != BLOCK_SIZE) return null

        // RapiCa packs month*100+day and hour*100+minute into two 12-bit fields.
        val packedDate = (u(block[0]) shl 4) or (u(block[1]) shr 4)
        val month = packedDate / 100
        val day = packedDate % 100
        val packedTime = ((u(block[1]) and 0x0f) shl 8) or u(block[2])
        val hour = packedTime / 100
        val minute = packedTime % 100
        val timestamp = inferTimestamp(month, day, hour, minute, notAfter) ?: return null

        val companyCode = u(block[3])
        val isIwasaki = (companyCode and 0xf0) == 0x40
        val stopOffset = if (isIwasaki) 5 else 4
        val stopCode = u24(block, stopOffset) ?: return null
        val lineOffset = if (isIwasaki) 8 else 7
        val lineCode = u16(block, lineOffset) ?: return null
        val kindCode = u(block[12])
        val balance = u24(block, 13) ?: return null
        if (balance > MAX_BALANCE) return null

        return HistoryRecord(
            raw = block.copyOf(),
            timestamp = timestamp,
            companyCode = companyCode,
            stopCode = stopCode,
            lineCode = lineCode,
            kindCode = kindCode,
            balance = balance
        )
    }

    private fun inferTimestamp(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        notAfter: LocalDateTime
    ): LocalDateTime? {
        if (month !in 1..12 || hour !in 0..23 || minute !in 0..59) return null
        for (year in notAfter.year downTo notAfter.year - 10) {
            val candidate = runCatching {
                LocalDate.of(year, month, day).atTime(hour, minute)
            }.getOrNull() ?: continue
            if (!candidate.isAfter(notAfter)) return candidate
        }
        return null
    }

    private fun toCards(
        records: List<HistoryRecord>,
        cardId: String,
        context: android.content.Context,
        yearEstimated: Boolean
    ): List<Card> {
        val pairedBoardingIndices = mutableSetOf<Int>()
        val boardingIndexByAlightingIndex = mutableMapOf<Int, Int>()

        records.forEachIndexed { alightingIndex, alighting ->
            if (alighting.kindCode != KIND_GETOFF) return@forEachIndexed
            val boardingIndex = (alightingIndex + 1 until records.size).firstOrNull { candidateIndex ->
                records[candidateIndex].kindCode == KIND_GETON && candidateIndex !in pairedBoardingIndices
            } ?: return@forEachIndexed
            boardingIndexByAlightingIndex[alightingIndex] = boardingIndex
            pairedBoardingIndices.add(boardingIndex)
        }

        return records.mapIndexedNotNull { index, record ->
            if (index in pairedBoardingIndices) return@mapIndexedNotNull null
            val boardingIndex = boardingIndexByAlightingIndex[index]
            val boarding = boardingIndex?.let(records::get)
            val balanceBefore = records.getOrNull(index + 1)?.balance
            val balanceChange = balanceBefore?.let { record.balance - it }
            val stop = RapicaStopDatabase.find(
                context = context,
                companyCode = record.companyCode,
                stopCode = record.stopCode,
                date = record.timestamp.toLocalDate()
            )
            val boardingStop = boarding?.let {
                RapicaStopDatabase.find(context, it.companyCode, it.stopCode, it.timestamp.toLocalDate())
            }

            val action = when {
                record.kindCode == KIND_CHARGE -> "チャージ"
                record.kindCode == KIND_GETON && boardingIndex == null -> "バス乗車"
                record.kindCode == KIND_GETOFF && boarding != null -> "バス利用"
                record.kindCode == KIND_GETOFF -> "バス降車"
                record.kindCode == KIND_CREATE -> "カード発行"
                record.kindCode == KIND_REGISTER -> "カード登録"
                else -> "RapiCa処理(%02X)".format(Locale.US, record.kindCode)
            }
            val activityKind = when (record.kindCode) {
                KIND_GETON, KIND_GETOFF -> "バス"
                KIND_CHARGE -> "チャージ"
                else -> "RapiCa"
            }
            val companyName = companyName(record.companyCode)
            val boardingCompanyName = boarding?.let { companyName(it.companyCode) }
            val rawHex = record.raw.joinToString("") { "%02X".format(Locale.US, u(it)) }
            val inRoute = boardingStop?.routeName ?: if (boarding != null) {
                "系統 %04X".format(Locale.US, boarding.lineCode)
            } else {
                stop?.routeName ?: if (record.kindCode == KIND_GETON || record.kindCode == KIND_GETOFF) {
                    "系統 %04X".format(Locale.US, record.lineCode)
                } else null
            }
            val outRoute = if (boarding != null) {
                stop?.routeName ?: "系統 %04X".format(Locale.US, record.lineCode)
            } else null
            val memo = buildString {
                append("時刻 ")
                append("%02d:%02d".format(Locale.US, record.timestamp.hour, record.timestamp.minute))
                if (yearEstimated) append("。履歴の年は月日データから推定")
                if (stop == null) append("。停留所コード 0x%06X".format(Locale.US, record.stopCode))
            }

            Card(
                cardId = cardId,
                date = "%04d/%02d/%02d".format(
                    Locale.US,
                    record.timestamp.year,
                    record.timestamp.monthValue,
                    record.timestamp.dayOfMonth
                ),
                number = "Rapica-$rawHex",
                amount = balanceChange?.toString(),
                kind = activityKind,
                device = "RapiCa",
                action = action,
                inLine = inRoute,
                inStation = boardingStop?.name ?: if (boarding == null) stop?.name else null,
                outLine = outRoute,
                outStation = if (boarding != null) stop?.name else null,
                balance = record.balance.toString(),
                inCompany = boardingCompanyName ?: companyName,
                outCompany = if (boarding != null) companyName else null,
                memo = memo,
                internalCode = "Rapica System=8194 Kind=%02X Company=%02X Stop=%06X BusLine=%04X Balance=%d Record=$rawHex".format(
                    Locale.US,
                    record.kindCode,
                    record.companyCode,
                    record.stopCode,
                    record.lineCode,
                    record.balance
                )
            )
        }.distinctBy { it.number }
    }

    private fun companyName(companyCode: Int): String = when ((companyCode shr 4) and 0x0f) {
        1 -> "鹿児島市交通局"
        2 -> "南国交通"
        3 -> "JR九州バス"
        4 -> "いわさきコーポレーション"
        5 -> "林田バス"
        else -> "RapiCa"
    }

    private fun u24(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || bytes.size - offset < 3) return null
        return (u(bytes[offset]) shl 16) or (u(bytes[offset + 1]) shl 8) or u(bytes[offset + 2])
    }

    private fun u16(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || bytes.size - offset < 2) return null
        return (u(bytes[offset]) shl 8) or u(bytes[offset + 1])
    }

    private fun u(value: Byte): Int = value.toInt() and 0xff

    private const val KIND_CREATE = 0x00
    private const val KIND_REGISTER = 0x10
    private const val KIND_GETON = 0x30
    private const val KIND_CHARGE = 0x40
    private const val KIND_GETOFF = 0x41
}
