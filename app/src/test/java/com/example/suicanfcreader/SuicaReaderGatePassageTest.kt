package com.example.suicanfcreader

import com.example.suicanfcreader.lib.SuicaReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuicaReaderGatePassageTest {
    @Test
    fun parsesEntryRecordAndBcdTime() {
        val block = gateBlock(gateType = 0xa0, line = 0x12, station = 0x34, hour = 0x08, minute = 0x47)

        val record = SuicaReader.parseGatePassageRecord(block)

        assertEquals(0x12, record?.lineCode)
        assertEquals(0x34, record?.stationCode)
        assertTrue(record?.isEntry == true)
        assertEquals("2026/09/23", record?.date)
        assertEquals("08:47", record?.time)
    }

    @Test
    fun parsesExitRecord() {
        val block = gateBlock(gateType = 0x20, line = 0x12, station = 0x34, hour = 0x23, minute = 0x59)

        val record = SuicaReader.parseGatePassageRecord(block)

        assertFalse(record?.isEntry ?: true)
        assertEquals("23:59", record?.time)
    }

    @Test
    fun rejectsInvalidBcdTime() {
        val block = gateBlock(gateType = 0xa0, line = 0x12, station = 0x34, hour = 0x2a, minute = 0x00)

        assertNull(SuicaReader.parseGatePassageRecord(block))
    }

    @Test
    fun canBuildRequestForGatePassageService() {
        val request = SuicaReader.readWithoutEncryption(
            idm = ByteArray(8),
            startBlock = 0,
            size = 3,
            serviceCode = SuicaReader.SERVICE_GATE_PASSAGE_HISTORY
        )

        assertEquals(0x8f.toByte(), request[11])
        assertEquals(0x10.toByte(), request[12])
    }

    @Test
    fun parsesAllFourStationCodesForMatchingGatePassageRecords() {
        val codes = SuicaReader.parseGatePassageStationCodes("Area=7 In=12/34 Out=56/78")

        assertEquals(12, codes?.inLineCode)
        assertEquals(34, codes?.inStationCode)
        assertEquals(56, codes?.outLineCode)
        assertEquals(78, codes?.outStationCode)
    }

    @Test
    fun rejectsMalformedStationCodesWithoutThrowing() {
        assertNull(SuicaReader.parseGatePassageStationCodes("Area=7 In=12/34 Out=56"))
    }

    private fun gateBlock(
        gateType: Int,
        line: Int,
        station: Int,
        hour: Int,
        minute: Int
    ): ByteArray = ByteArray(16).apply {
        this[0] = gateType.toByte()
        this[2] = line.toByte()
        this[3] = station.toByte()
        val dateBits = (26 shl 9) or (9 shl 5) or 23
        this[6] = (dateBits shr 8).toByte()
        this[7] = dateBits.toByte()
        this[8] = hour.toByte()
        this[9] = minute.toByte()
    }
}
