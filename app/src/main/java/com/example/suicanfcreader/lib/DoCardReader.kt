package com.example.suicanfcreader.lib

import com.example.suicanfcreader.model.Card
import java.time.LocalDate
import java.util.Locale

/**
 * Read-only parser for the public DoCARD history layout.
 *
 * DoCARD uses a separate FeliCa system from the Suica-compatible 0x0003 system. Keep
 * its service and record interpretation isolated so this path is only used after an
 * exact system-code match. The record layout notes are based on the MIT-licensed
 * DoCard_Reader project and its CC BY 4.0 technical article by mimoex.
 */
object DoCardReader {
    const val SYSTEM_CODE = 0x8DB6
    const val SERVICE_HISTORY = 0x010F
    const val MAX_HISTORY_BLOCKS = 20
    private const val RESPONSE_HEADER_SIZE = 13
    private const val BLOCK_SIZE = 16

    @JvmStatic
    fun matchesSystemCode(systemCode: ByteArray?): Boolean =
        systemCode?.size == 2 &&
            unsigned(systemCode[0]) == (SYSTEM_CODE shr 8) &&
            unsigned(systemCode[1]) == (SYSTEM_CODE and 0xff)

    /**
     * Validates and extracts blocks from a Read Without Encryption response.
     * Returns null for any malformed, failed, or mismatched response.
     */
    @JvmStatic
    fun parseHistoryResponse(
        response: ByteArray,
        expectedIdm: ByteArray,
        requestedBlocks: Int
    ): List<ByteArray>? {
        if (expectedIdm.size != 8 || requestedBlocks !in 1..10) return null
        if (response.size < RESPONSE_HEADER_SIZE) return null
        if (unsigned(response[0]) != response.size || unsigned(response[1]) != 0x07) return null
        if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) return null
        if (unsigned(response[10]) != 0 || unsigned(response[11]) != 0) return null

        val blockCount = unsigned(response[12])
        if (blockCount > requestedBlocks || blockCount > 10) return null
        if (response.size != RESPONSE_HEADER_SIZE + blockCount * BLOCK_SIZE) return null

        return (0 until blockCount).map { index ->
            val start = RESPONSE_HEADER_SIZE + index * BLOCK_SIZE
            response.copyOfRange(start, start + BLOCK_SIZE)
        }
    }

    /**
     * Builds visible read-only history records. DoCARD stores month/day but not a
     * complete year in the documented fields, so the year is estimated from today
     * while walking newest-to-oldest history.
     */
    @JvmStatic
    fun parseHistoryBlocks(blocks: List<ByteArray>, today: LocalDate = LocalDate.now()): List<Card> {
        var previousDate = today
        return blocks.mapNotNull { block ->
            if (block.size != BLOCK_SIZE) return@mapNotNull null
            val month = unsigned(block[0])
            val day = unsigned(block[1])
            val date = latestDateOnOrBefore(month, day, previousDate) ?: return@mapNotNull null
            previousDate = date

            val sequence = unsigned(block[11])
            val processType = unsigned(block[12])
            val recordedAmount = (unsigned(block[9]) shl 8) or unsigned(block[10])
            val balance = (unsigned(block[14]) shl 8) or unsigned(block[15])
            Card(
                date = "%04d/%02d/%02d".format(Locale.US, date.year, date.monthValue, date.dayOfMonth),
                // A raw-record fingerprint stays stable when the estimated year changes and
                // avoids collisions if the one-byte sequence number wraps.
                number = "DoCARD-" + block.joinToString("") {
                    "%02X".format(Locale.US, unsigned(it))
                },
                kind = "DoCARD",
                device = "DoCARD",
                action = "DoCARD利用",
                balance = balance.toString(),
                memo = "年月日の年は月日データから推定",
                internalCode = "DoCARD System=8DB6 Type=%02X AmountRaw=%d Sequence=%d".format(
                    Locale.US,
                    processType,
                    recordedAmount,
                    sequence
                )
            )
        }
    }

    private fun latestDateOnOrBefore(month: Int, day: Int, notAfter: LocalDate): LocalDate? {
        for (year in notAfter.year downTo notAfter.year - 2) {
            val candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
            if (!candidate.isAfter(notAfter)) return candidate
        }
        return null
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xff
}
