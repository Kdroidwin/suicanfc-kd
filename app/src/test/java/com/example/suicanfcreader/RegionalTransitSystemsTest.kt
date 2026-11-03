package com.example.suicanfcreader

import com.example.suicanfcreader.lib.RegionalTransitSystems
import com.example.suicanfcreader.lib.SuicaReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalTransitSystemsTest {
    @Test
    fun recognizesOnlyExplicitlySupportedRegionalSystemCodes() {
        assertEquals("IruCa", RegionalTransitSystems.fromSystemCode(code(0x80de))?.displayName)
        assertEquals("SAPICA", RegionalTransitSystems.fromSystemCode(code(0x865e))?.displayName)
        assertEquals("OKICA", RegionalTransitSystems.fromSystemCode(code(0x8fc1))?.displayName)
        assertEquals("りゅーと", RegionalTransitSystems.fromSystemCode(code(0x8b5d))?.displayName)
        assertNull(RegionalTransitSystems.fromSystemCode(code(0x0003)))
        assertNull(RegionalTransitSystems.fromSystemCode(code(0x8db6)))
    }

    @Test
    fun markerRetainsTheExistingStationCodePayloadAndCanBeResolved() {
        val regional = RegionalTransitSystems.fromSystemCode(code(0x865e))!!
        val marker = RegionalTransitSystems.marker(regional, "Area=1 In=2/3 Out=4/5")

        assertEquals("SAPICA", RegionalTransitSystems.fromMarker(marker)?.displayName)
        assertTrue(marker.endsWith("Area=1 In=2/3 Out=4/5"))
    }

    @Test
    fun gateHistoryIsOnlyEnabledForSystemsWithConfirmedSupport() {
        assertTrue(RegionalTransitSystems.fromSystemCode(code(0x80de))!!.supportsGatePassageHistory)
        assertTrue(RegionalTransitSystems.fromSystemCode(code(0x865e))!!.supportsGatePassageHistory)
        assertTrue(RegionalTransitSystems.fromSystemCode(code(0x8fc1))!!.supportsGatePassageHistory)
        assertEquals(false, RegionalTransitSystems.fromSystemCode(code(0x8b5d))!!.supportsGatePassageHistory)
    }

    @Test
    fun gateStationCodesRemainReadableAfterAddingRegionalSystemMarker() {
        val regional = RegionalTransitSystems.fromSystemCode(code(0x865e))!!
        val internalCode = RegionalTransitSystems.marker(regional, "Area=1 In=2/3 Out=4/5")

        assertEquals(
            com.example.suicanfcreader.lib.GatePassageStationCodes(2, 3, 4, 5),
            SuicaReader.parseGatePassageStationCodes(internalCode)
        )
    }

    private fun code(value: Int): ByteArray =
        byteArrayOf((value shr 8).toByte(), value.toByte())
}
