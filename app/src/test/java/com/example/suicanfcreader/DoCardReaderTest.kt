package com.example.suicanfcreader

import com.example.suicanfcreader.lib.DoCardReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoCardReaderTest {
    @Test
    fun matchesOnlyTheDoCardSystemCode() {
        assertTrue(DoCardReader.matchesSystemCode(byteArrayOf(0x8d.toByte(), 0xb6.toByte())))
        assertFalse(DoCardReader.matchesSystemCode(byteArrayOf(0xb6.toByte(), 0x8d.toByte())))
        assertFalse(DoCardReader.matchesSystemCode(null))
    }

    @Test
    fun parsesDocumentedHistorySampleWithoutInventingTransactionSemantics() {
        val block = bytes("02 11 0e 01 09 27 1a 2e 22 01 04 45 02 00 02 26")

        val card = DoCardReader.parseHistoryBlocks(listOf(block), LocalDate.of(2026, 11, 3)).single()

        assertEquals("2026/02/17", card.date)
        assertEquals("550", card.balance)
        assertEquals("DoCARD", card.kind)
        assertEquals("DoCARD利用", card.action)
        assertEquals("年月日の年は月日データから推定", card.memo)
        assertTrue(card.internalCode.orEmpty().contains("AmountRaw=260"))
    }

    @Test
    fun estimatesYearAcrossNewYearForNewestToOldestRecords() {
        val newestJanuary = block(month = 1, day = 2, sequence = 2, balance = 500)
        val oldestDecember = block(month = 12, day = 28, sequence = 1, balance = 700)

        val cards = DoCardReader.parseHistoryBlocks(
            listOf(newestJanuary, oldestDecember),
            LocalDate.of(2026, 11, 3)
        )

        assertEquals("2026/01/02", cards[0].date)
        assertEquals("2025/12/28", cards[1].date)
    }

    @Test
    fun keepsRecordKeyStableWhenEstimatedYearChanges() {
        val block = block(month = 2, day = 17, sequence = 0x45, balance = 550)

        val firstRead = DoCardReader.parseHistoryBlocks(listOf(block), LocalDate.of(2026, 11, 3)).single()
        val laterRead = DoCardReader.parseHistoryBlocks(listOf(block), LocalDate.of(2027, 3, 1)).single()

        assertEquals("2026/02/17", firstRead.date)
        assertEquals("2027/02/17", laterRead.date)
        assertEquals(firstRead.number, laterRead.number)
    }

    @Test
    fun rejectsMalformedFelicaHistoryResponses() {
        val idm = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val response = ByteArray(29).apply {
            this[0] = size.toByte()
            this[1] = 0x07
            idm.copyInto(this, destinationOffset = 2)
            this[12] = 1
            block().copyInto(this, destinationOffset = 13)
        }

        assertEquals(1, DoCardReader.parseHistoryResponse(response, idm, 1)?.size)
        assertNull(DoCardReader.parseHistoryResponse(response, idm, 0))
        assertNull(DoCardReader.parseHistoryResponse(response, idm + 1, 1))
        assertNull(DoCardReader.parseHistoryResponse(response.copyOf().also { it[10] = 1 }, idm, 1))
    }

    private fun block(
        month: Int = 2,
        day: Int = 17,
        sequence: Int = 1,
        balance: Int = 500
    ): ByteArray = ByteArray(16).apply {
        this[0] = month.toByte()
        this[1] = day.toByte()
        this[11] = sequence.toByte()
        this[12] = 2
        this[14] = (balance shr 8).toByte()
        this[15] = balance.toByte()
    }

    private fun bytes(values: String): ByteArray =
        values.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
