package com.example.suicanfcreader.lib

import android.nfc.tech.NfcF
import android.content.Context
import com.example.suicanfcreader.model.Card
import com.example.suicanfcreader.model.NicePassStopDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Dedicated, read-only reader for Enshu Railway's NicePass FeliCa system (0x040F).
 * The service identifiers and bit layout follow the MIT-licensed felica-look v0.2.0
 * implementation (nicepass.go / nicepass.h / nicepass_get.c). Unknown procedures are
 * retained as numeric NicePass events and are never sent to the Suica history parser.
 * https://github.com/gnue/felica-look/tree/v0.2.0/nicepass
 */
object NicePassReader {
    const val SYSTEM_CODE = 0x040F
    const val SERVICE_HISTORY = 0x030F

    private const val RESPONSE_HEADER_SIZE = 13
    private const val BLOCK_SIZE = 16
    private const val MAX_HISTORY_BLOCKS = 50
    private const val MAX_BLOCKS_PER_REQUEST = 10
    private const val MAX_BALANCE = 1_000_000
    private const val MAX_AMOUNT = 100_000

    data class ReadResult(
        val cards: List<Card>,
        val rawResponses: List<ByteArray>
    )

    private data class HistoryRecord(
        val timestamp: LocalDateTime,
        val train: Int,
        val inStation: Int,
        val outStation: Int,
        val terminalType: Int,
        val procedure: Int,
        val useKind: Int,
        val amount: Int,
        val balance: Int
    )

    private data class ReadChunk(
        val blocks: List<ByteArray>,
        val response: ByteArray,
        val requestedBlocks: Int
    )

    @JvmStatic
    fun matchesSystemCode(systemCode: ByteArray?): Boolean =
        systemCode?.size == 2 &&
            u(systemCode[0]) == (SYSTEM_CODE shr 8) &&
            u(systemCode[1]) == (SYSTEM_CODE and 0xff)

    /** Must be called after NfcF.connect(). */
    fun read(felica: NfcF, idm: ByteArray, cardId: String, context: Context): ReadResult? {
        if (idm.size != 8) return null
        val (blocks, rawResponses) = readHistory(felica, idm) ?: return null
        val records = blocks.mapNotNull(::parseRecord)
        val cards = records.mapNotNull { toCard(it, cardId, context) }.distinctBy { it.number }
        return cards.takeIf { it.isNotEmpty() }?.let { ReadResult(it, rawResponses) }
    }

    private fun readHistory(felica: NfcF, idm: ByteArray): Pair<List<ByteArray>, List<ByteArray>>? {
        val blocks = mutableListOf<ByteArray>()
        val responses = mutableListOf<ByteArray>()
        var startBlock = 0

        while (startBlock < MAX_HISTORY_BLOCKS) {
            val desired = minOf(MAX_BLOCKS_PER_REQUEST, MAX_HISTORY_BLOCKS - startBlock)
            val chunk = readChunk(felica, idm, startBlock, desired) ?: if (startBlock == 0) return null else break
            if (chunk.blocks.isEmpty()) break
            blocks += chunk.blocks
            responses += chunk.response
            startBlock += chunk.blocks.size
            if (chunk.blocks.size < chunk.requestedBlocks) break
        }
        return blocks.takeIf { it.isNotEmpty() }?.let { it to responses }
    }

    private fun readChunk(felica: NfcF, idm: ByteArray, startBlock: Int, desired: Int): ReadChunk? {
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
                        serviceCode = SERVICE_HISTORY
                    )
                )
            }.getOrNull() ?: continue
            val parsed = parseResponse(response, idm, requestSize) ?: continue
            return ReadChunk(parsed, response, requestSize)
        }
        return null
    }

    @JvmStatic
    fun parseResponse(response: ByteArray, expectedIdm: ByteArray, requestedBlocks: Int): List<ByteArray>? {
        if (expectedIdm.size != 8 || requestedBlocks !in 1..MAX_BLOCKS_PER_REQUEST) return null
        if (response.size < RESPONSE_HEADER_SIZE || response.size > 255) return null
        if (u(response[0]) != response.size || u(response[1]) != 0x07) return null
        if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) return null
        if (u(response[10]) != 0 || u(response[11]) != 0) return null
        val count = u(response[12])
        if (count > requestedBlocks || response.size != RESPONSE_HEADER_SIZE + count * BLOCK_SIZE) return null
        return List(count) { index ->
            val offset = RESPONSE_HEADER_SIZE + index * BLOCK_SIZE
            response.copyOfRange(offset, offset + BLOCK_SIZE)
        }
    }

    private fun parseRecord(block: ByteArray): HistoryRecord? {
        if (block.size != BLOCK_SIZE) return null
        val year = 2000 + (u(block[0]) shr 1)
        val month = ((u(block[0]) and 1) shl 3) or (u(block[1]) shr 5)
        val day = u(block[1]) and 0x1F
        val minutesAfterMidnight = u(block[2]) * 10
        if (year !in 2000..LocalDateTime.now().year + 1 || minutesAfterMidnight !in 0..1439) return null
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val timestamp = date.atStartOfDay().plusMinutes(minutesAfterMidnight.toLong())
        if (timestamp.isAfter(LocalDateTime.now().plusDays(1))) return null

        val train = u16(block, 3)
        val stationBits = u40(block, 5) ?: return null
        val inStation = ((stationBits shr 20) and 0xFFFFF).toInt()
        val outStation = (stationBits and 0xFFFFF).toInt()
        val procedureByte = u(block[10])
        val terminalType = procedureByte shr 4
        val procedure = procedureByte and 0x0F
        val useKind = u(block[11]) shr 4
        val rawAmount = ((u(block[11]) and 0x0F) shl 8) or u(block[12])
        val amount = if (rawAmount and 0x0800 != 0) rawAmount - 0x1000 else rawAmount
        val amountYen = amount * 10
        val balance = u16(block, 13)
        if (amountYen !in -MAX_AMOUNT..MAX_AMOUNT || balance !in 0..MAX_BALANCE) return null

        return HistoryRecord(
            timestamp = timestamp,
            train = train,
            inStation = inStation,
            outStation = outStation,
            terminalType = terminalType,
            procedure = procedure,
            useKind = useKind,
            amount = amountYen,
            balance = balance
        )
    }

    private fun toCard(record: HistoryRecord, cardId: String, context: Context): Card {
        val inStop = record.inStation.takeIf { it != 0 }?.let {
            NicePassStopDatabase.find(context, it, record.timestamp.toLocalDate())
        }
        val outStop = record.outStation.takeIf { it != 0 }?.let {
            NicePassStopDatabase.find(context, it, record.timestamp.toLocalDate())
        }
        val isTransit = record.inStation != 0 || record.outStation != 0
        val vehicle = listOfNotNull(inStop?.vehicle, outStop?.vehicle).distinct().singleOrNull()
        val transitKind = when {
            record.terminalType == 5 || vehicle == "B" -> "バス"
            vehicle == "T" -> "鉄道"
            isTransit -> "交通"
            else -> null
        }
        val typeName = terminalTypeName(record.terminalType)
        val action = when (record.procedure) {
            1 -> "入場"
            2 -> "運賃支払"
            7 -> "窓口処理"
            8 -> "券売機処理"
            else -> when {
                record.amount > 0 -> "チャージ"
                record.amount < 0 -> "支払"
                else -> "処理"
            }
        }
        val key = listOf(
            record.timestamp.format(TIMESTAMP_FORMAT),
            record.train,
            record.inStation,
            record.outStation,
            record.terminalType,
            record.procedure,
            record.amount,
            record.balance
        ).joinToString("-")
        val memo = buildString {
            append("時刻 ")
            append(record.timestamp.format(TIME_FORMAT))
            append("。端末種 ")
            append(typeName)
            append("。利用金額種別 ")
            append(record.useKind)
            append("。装置番号 %04X".format(Locale.US, record.train))
            if (record.inStation != 0 && inStop == null) append("。乗車駅コード %05X".format(Locale.US, record.inStation))
            if (record.outStation != 0 && outStop == null) append("。降車駅コード %05X".format(Locale.US, record.outStation))
        }
        val inOperator = inStop?.operatorName
        val outOperator = outStop?.operatorName

        return Card(
            cardId = cardId,
            date = record.timestamp.toLocalDate().format(DATE_FORMAT),
            number = "NicePass-$key",
            amount = record.amount.toString(),
            kind = when {
                transitKind != null -> transitKind
                record.amount > 0 -> "チャージ"
                record.amount < 0 -> "物販"
                else -> "NicePass"
            },
            device = typeName,
            action = "ナイスパス $action",
            inLine = null,
            inStation = record.inStation.takeIf { it != 0 }?.let { inStop?.name ?: "駅・停留所 %05X".format(Locale.US, it) },
            outLine = null,
            outStation = record.outStation.takeIf { it != 0 }?.let { outStop?.name ?: "駅・停留所 %05X".format(Locale.US, it) },
            balance = record.balance.toString(),
            inCompany = inOperator,
            outCompany = outOperator,
            memo = memo,
            internalCode = "NicePass System=040F Type=%X Proc=%X UseKind=%X Train=%04X In=%05X Out=%05X Balance=%d".format(
                Locale.US,
                record.terminalType,
                record.procedure,
                record.useKind,
                record.train,
                record.inStation,
                record.outStation,
                record.balance
            )
        )
    }

    private fun terminalTypeName(type: Int): String = when (type) {
        1 -> "鉄道改札機（入場）"
        2 -> "鉄道改札機"
        5 -> "バス車載機"
        7 -> "窓口処理機"
        8 -> "券売機"
        10 -> "入金機"
        else -> "端末種 %X".format(Locale.US, type)
    }

    private fun u40(data: ByteArray, offset: Int): Long? {
        if (offset < 0 || data.size - offset < 5) return null
        return (u(data[offset]).toLong() shl 32) or
            (u(data[offset + 1]).toLong() shl 24) or
            (u(data[offset + 2]).toLong() shl 16) or
            (u(data[offset + 3]).toLong() shl 8) or
            u(data[offset + 4]).toLong()
    }

    private fun u16(data: ByteArray, offset: Int): Int = (u(data[offset]) shl 8) or u(data[offset + 1])
    private fun u(value: Byte): Int = value.toInt() and 0xFF

    private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val TIMESTAMP_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.US)
}
