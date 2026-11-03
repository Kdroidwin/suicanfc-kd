package com.example.suicanfcreader.lib

import android.nfc.tech.NfcF
import com.example.suicanfcreader.model.Card
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Read-only history parsers for the three common FE00 e-money profiles.
 *
 * Service identifiers and record layouts are based on the MIT-licensed
 * TRETJapanNFCReader / felica-reader implementations. WAON's packed history fields
 * are reverse-engineered; those records are only accepted when the paired terminal,
 * transaction type, date, balance, and amount all pass validation.
 */
object JapaneseEMoneyReader {
    const val SERVICE_EDY_HISTORY = 0x170F
    const val SERVICE_NANACO_HISTORY = 0x564F
    const val SERVICE_WAON_HISTORY = 0x680B

    private const val SYSTEM_COMMON_AREA = 0xFE00
    private const val SYSTEM_WAON = 0x852B
    private const val RESPONSE_HEADER_SIZE = 13
    private const val BLOCK_SIZE = 16
    private const val MAX_BLOCKS_PER_REQUEST = 10
    private const val MAX_RECORD_AMOUNT = 100_000
    private const val MAX_BALANCE = 100_000

    data class ReadResult(
        val brand: String,
        val cards: List<Card>,
        val rawResponses: List<ByteArray>
    )

    @JvmStatic
    fun handlesSystemCode(systemCode: Int): Boolean =
        systemCode == SYSTEM_COMMON_AREA || systemCode == SYSTEM_WAON

    private data class ParsedRecord(val timestamp: LocalDateTime, val card: Card)

    /**
     * FE00 is shared by several services, so identify the profile by successfully
     * reading and validating its own history service. A failed probe is not treated
     * as a match; the caller shows a system-specific notice instead of trying the
     * transit-history decoder on e-money data.
     */
    fun read(felica: NfcF, idm: ByteArray, cardId: String): ReadResult? {
        if (idm.size != 8) return null
        val systemCode = felica.systemCode.toUnsignedInt() ?: return null
        val profiles = when (systemCode) {
            SYSTEM_COMMON_AREA -> listOf(
                ::readEdy,
                ::readNanaco,
                ::readWaon
            )
            SYSTEM_WAON -> listOf(::readWaon)
            else -> return null
        }

        for (profile in profiles) {
            val result = runCatching { profile(felica, idm, cardId) }.getOrNull()
            if (result != null && result.cards.isNotEmpty()) return result
        }
        return null
    }

    private fun readEdy(felica: NfcF, idm: ByteArray, cardId: String): ReadResult? {
        val response = readBlocks(felica, idm, SERVICE_EDY_HISTORY, 0, 6).firstOrNull() ?: return null
        val records = response.blocks.mapNotNull { block -> parseEdyRecord(block, cardId) }
        return result("楽天Edy", "Edy", records, listOf(response.raw))
    }

    private fun readNanaco(felica: NfcF, idm: ByteArray, cardId: String): ReadResult? {
        val responses = mutableListOf<ValidResponse>()
        val blocks = mutableListOf<ByteArray>()
        for (startBlock in 0 until NANACO_HISTORY_BLOCKS step MAX_BLOCKS_PER_REQUEST) {
            val response = try {
                readBlocks(
                    felica,
                    idm,
                    SERVICE_NANACO_HISTORY,
                    startBlock,
                    minOf(MAX_BLOCKS_PER_REQUEST, NANACO_HISTORY_BLOCKS - startBlock)
                ).firstOrNull()
            } catch (_: Exception) {
                if (startBlock == 0) return null
                break
            } ?: if (startBlock == 0) return null else break

            responses.add(response)
            blocks.addAll(response.blocks)
            if (response.blocks.size < MAX_BLOCKS_PER_REQUEST) break
        }
        val records = blocks.mapNotNull { block -> parseNanacoRecord(block, cardId) }
        return result("nanaco", "Nanaco", records, responses.map { it.raw })
    }

    private fun readWaon(felica: NfcF, idm: ByteArray, cardId: String): ReadResult? {
        val response = readBlocks(felica, idm, SERVICE_WAON_HISTORY, 0, 9).firstOrNull() ?: return null
        val records = response.blocks.take(6).chunked(2).mapNotNull { pair ->
            if (pair.size != 2) null else parseWaonRecord(pair[0], pair[1], cardId)
        }
        return result("WAON", "Waon", records, listOf(response.raw))
    }

    private fun result(
        brand: String,
        marker: String,
        records: List<ParsedRecord>,
        rawResponses: List<ByteArray>
    ): ReadResult? {
        val cards = records
            .sortedByDescending { it.timestamp }
            .distinctBy { it.card.number }
            .map { it.card.apply { internalCode = "JapaneseEMoney=$marker ${internalCode.orEmpty()}".trim() } }
        return cards.takeIf { it.isNotEmpty() }?.let { ReadResult(brand, it, rawResponses) }
    }

    private fun parseEdyRecord(block: ByteArray, cardId: String): ParsedRecord? {
        if (block.size != BLOCK_SIZE) return null
        val transactionType = u(block[0])
        val action = when (transactionType) {
            0x02, 0x04 -> "チャージ"
            0x20 -> "支払"
            else -> return null
        }
        val days = (u(block[4]) shl 7) or (u(block[5]) shr 1)
        val seconds = ((u(block[5]) and 1) shl 16) or (u(block[6]) shl 8) or u(block[7])
        if (seconds !in 0 until SECONDS_PER_DAY || days > MAX_EDY_DAYS) return null
        val timestamp = try {
            EDY_EPOCH.plusDays(days.toLong()).plusSeconds(seconds.toLong())
        } catch (_: Exception) {
            return null
        }
        if (timestamp.isAfter(LocalDateTime.now().plusDays(1))) return null

        val amount = u32(block, 8) ?: return null
        val balance = u32(block, 12) ?: return null
        if (amount !in 1..MAX_RECORD_AMOUNT || balance !in 0..MAX_BALANCE) return null
        val sequence = u16(block, 2)
        return ParsedRecord(
            timestamp,
            transactionCard(
                cardId = cardId,
                brand = "楽天Edy",
                marker = "Edy",
                timestamp = timestamp,
                sequence = sequence,
                transactionType = transactionType,
                amount = if (action == "支払") -amount else amount,
                balance = balance,
                kind = if (action == "支払") "物販" else "チャージ",
                action = action,
            )
        )
    }

    private fun parseNanacoRecord(block: ByteArray, cardId: String): ParsedRecord? {
        if (block.size != BLOCK_SIZE) return null
        val transactionType = u(block[0])
        val action = when (transactionType) {
            0x47 -> "支払"
            0x6F -> "チャージ"
            else -> return null
        }
        val amount = u32(block, 1) ?: return null
        val balance = u32(block, 5) ?: return null
        val packedDateTime = u32(block, 9) ?: return null
        val year = (packedDateTime shr 21) + 2000
        val month = (packedDateTime shr 17) and 0x0F
        val day = (packedDateTime shr 12) and 0x1F
        val hour = (packedDateTime shr 6) and 0x3F
        val minute = packedDateTime and 0x3F
        val timestamp = validDateTime(year, month, day, hour, minute) ?: return null
        if (timestamp.isAfter(LocalDateTime.now().plusDays(1))) return null
        if (amount !in 1..MAX_RECORD_AMOUNT || balance !in 0..MAX_BALANCE) return null
        val sequence = u16(block, 13)
        return ParsedRecord(
            timestamp,
            transactionCard(
                cardId = cardId,
                brand = "nanaco",
                marker = "Nanaco",
                timestamp = timestamp,
                sequence = sequence,
                transactionType = transactionType,
                amount = if (action == "支払") -amount else amount,
                balance = balance,
                kind = if (action == "支払") "物販" else "チャージ",
                action = action,
            )
        )
    }

    private fun parseWaonRecord(
        terminalBlock: ByteArray,
        transactionBlock: ByteArray,
        cardId: String
    ): ParsedRecord? {
        if (terminalBlock.size != BLOCK_SIZE || transactionBlock.size != BLOCK_SIZE) return null
        val terminalId = terminalBlock.copyOfRange(0, 13)
        val printableCount = terminalId.count { u(it) in 0x20..0x7E }
        if (printableCount < 6) return null

        val transactionType = u(transactionBlock[1])
        val (action, kind) = when (transactionType) {
            0x04 -> "支払" to "物販"
            0x08, 0x28 -> "返金" to "チャージ"
            0x0C, 0x10, 0x30 -> "チャージ" to "チャージ"
            0x1C, 0x20 -> "支払・オートチャージ" to "物販"
            else -> return null
        }

        val packed = unsignedBigEndian(transactionBlock, 2, 11)
        val year = 2005 + packedField(packed, 83, 0x1F)
        val month = packedField(packed, 79, 0x0F)
        val day = packedField(packed, 74, 0x1F)
        val hour = packedField(packed, 69, 0x1F)
        val minute = packedField(packed, 63, 0x3F)
        val timestamp = validDateTime(year, month, day, hour, minute) ?: return null
        if (timestamp.isAfter(LocalDateTime.now().plusDays(1))) return null

        val balance = packedField(packed, 45, 0x3FFFF)
        val useAmount = packedField(packed, 27, 0x3FFFF)
        val chargeAmount = packedField(packed, 10, 0x1FFFF)
        if (balance !in 0..MAX_BALANCE || useAmount > MAX_RECORD_AMOUNT || chargeAmount > MAX_RECORD_AMOUNT) return null
        val amount = when (transactionType) {
            0x04 -> -useAmount
            0x08, 0x28 -> if (chargeAmount > 0) chargeAmount else useAmount
            0x0C, 0x10, 0x30 -> chargeAmount
            0x1C, 0x20 -> chargeAmount - useAmount
            else -> return null
        }
        if (amount == 0 || amount !in -MAX_RECORD_AMOUNT..MAX_RECORD_AMOUNT) return null

        val sequence = u16(terminalBlock, 13)
        val receipt = (u(terminalBlock[15]) shl 8) or u(transactionBlock[0])
        return ParsedRecord(
            timestamp,
            transactionCard(
                cardId = cardId,
                brand = "WAON",
                marker = "Waon",
                timestamp = timestamp,
                sequence = (sequence shl 16) or receipt,
                transactionType = transactionType,
                amount = amount,
                balance = balance,
                kind = kind,
                action = action,
            )
        )
    }

    private fun transactionCard(
        cardId: String,
        brand: String,
        marker: String,
        timestamp: LocalDateTime,
        sequence: Int,
        transactionType: Int,
        amount: Int,
        balance: Int,
        kind: String,
        action: String,
    ): Card = Card(
        cardId = cardId,
        date = timestamp.toLocalDate().format(DATE_FORMAT),
        // Use decoded fields for stable merging without persisting the raw terminal or
        // transaction bytes in the record key.
        number = "$sequence-${timestamp.format(TIMESTAMP_FORMAT)}-$transactionType-$amount-$balance",
        amount = amount.toString(),
        balance = balance.toString(),
        kind = kind,
        device = brand,
        action = "$brand $action",
        inCompany = brand,
        memo = "利用時刻 ${timestamp.format(TIME_FORMAT)}",
        internalCode = "JapaneseEMoney=$marker Type=%02X Sequence=%d".format(
            Locale.US,
            transactionType,
            sequence
        )
    )

    private fun readBlocks(
        felica: NfcF,
        idm: ByteArray,
        serviceCode: Int,
        startBlock: Int,
        blockCount: Int
    ): List<ValidResponse> {
        require(blockCount in 1..MAX_BLOCKS_PER_REQUEST)
        val response = felica.transceive(
            SuicaReader.readWithoutEncryption(idm, startBlock, blockCount, serviceCode)
        )
        val blocks = parseResponse(response, idm, blockCount) ?: return emptyList()
        return listOf(ValidResponse(response, blocks))
    }

    private fun parseResponse(response: ByteArray, idm: ByteArray, requestedBlocks: Int): List<ByteArray>? {
        if (idm.size != 8 || response.size < RESPONSE_HEADER_SIZE) return null
        if (u(response[0]) != response.size || u(response[1]) != 0x07) return null
        if (!response.copyOfRange(2, 10).contentEquals(idm)) return null
        if (u(response[10]) != 0 || u(response[11]) != 0) return null
        val count = u(response[12])
        if (count !in 1..requestedBlocks || response.size != RESPONSE_HEADER_SIZE + count * BLOCK_SIZE) return null
        return (0 until count).map { index ->
            val offset = RESPONSE_HEADER_SIZE + index * BLOCK_SIZE
            response.copyOfRange(offset, offset + BLOCK_SIZE)
        }
    }

    private data class ValidResponse(val raw: ByteArray, val blocks: List<ByteArray>)

    private fun ByteArray.toUnsignedInt(): Int? =
        takeIf { size == 2 }?.let { (u(it[0]) shl 8) or u(it[1]) }

    private fun validDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime? {
        if (year !in 2000..2099 || hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalDate.of(year, month, day).atTime(hour, minute) }.getOrNull()
    }

    private fun u32(data: ByteArray, offset: Int): Int? {
        if (offset < 0 || data.size - offset < 4) return null
        val value = (u(data[offset]).toLong() shl 24) or
            (u(data[offset + 1]).toLong() shl 16) or
            (u(data[offset + 2]).toLong() shl 8) or
            u(data[offset + 3]).toLong()
        return value.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun u16(data: ByteArray, offset: Int): Int = (u(data[offset]) shl 8) or u(data[offset + 1])

    private fun unsignedBigEndian(data: ByteArray, offset: Int, length: Int): java.math.BigInteger =
        java.math.BigInteger(1, data.copyOfRange(offset, offset + length))

    private fun packedField(value: java.math.BigInteger, shift: Int, mask: Long): Int =
        value.shiftRight(shift).and(java.math.BigInteger.valueOf(mask)).toInt()

    private fun u(value: Byte): Int = value.toInt() and 0xff

    private const val NANACO_HISTORY_BLOCKS = 20
    private const val SECONDS_PER_DAY = 86_400
    private const val MAX_EDY_DAYS = 32_000
    private val EDY_EPOCH = LocalDate.of(2000, 1, 1).atStartOfDay()
    private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val TIMESTAMP_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.US)
}
