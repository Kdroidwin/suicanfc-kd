package com.example.suicanfcreader.lib

/**
 * Regional FeliCa transit systems that expose the common 0x090F transaction service.
 * System-code/service-code support is cross-checked against the MIT-licensed
 * TRETJapanNFCReader project: https://github.com/treastrain/TRETJapanNFCReader
 */
data class RegionalTransitSystem(
    val systemCode: Int,
    val displayName: String,
    val historyServiceCode: Int = SuicaReader.SERVICE_TRANSACTION_HISTORY,
    val supportsGatePassageHistory: Boolean = false
)

object RegionalTransitSystems {
    private val supported = listOf(
        RegionalTransitSystem(0x80DE, "IruCa", supportsGatePassageHistory = true),
        RegionalTransitSystem(0x8592, "PASPY", supportsGatePassageHistory = true),
        RegionalTransitSystem(0x865E, "SAPICA", supportsGatePassageHistory = true),
        RegionalTransitSystem(0x8FC1, "OKICA", supportsGatePassageHistory = true),
        RegionalTransitSystem(0x8B5D, "りゅーと")
    )

    @JvmStatic
    fun fromSystemCode(systemCode: ByteArray?): RegionalTransitSystem? {
        if (systemCode?.size != 2) return null
        val code = ((systemCode[0].toInt() and 0xff) shl 8) or
            (systemCode[1].toInt() and 0xff)
        return supported.firstOrNull { it.systemCode == code }
    }

    @JvmStatic
    fun fromMarker(internalCode: String?): RegionalTransitSystem? {
        val match = SYSTEM_MARKER.find(internalCode.orEmpty()) ?: return null
        val code = match.groupValues[1].toIntOrNull(16) ?: return null
        return supported.firstOrNull { it.systemCode == code }
    }

    @JvmStatic
    fun marker(system: RegionalTransitSystem, existingInternalCode: String?): String =
        "FeliCaSystem=%04X %s".format(
            java.util.Locale.US,
            system.systemCode,
            existingInternalCode.orEmpty()
        ).trim()

    private val SYSTEM_MARKER = Regex("(?:^|\\s)FeliCaSystem=([0-9A-Fa-f]{4})(?:\\s|$)")
}
