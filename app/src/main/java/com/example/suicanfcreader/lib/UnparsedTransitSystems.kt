package com.example.suicanfcreader.lib

/**
 * Known local transit systems that need a proprietary transaction decoder.
 * System-code/name pairs are cross-referenced with the public Metrodroid FeliCa
 * system-code index. These systems are intercepted before the common 0x090F
 * history parser so their blocks cannot be mislabeled as Suica-compatible history.
 * https://github.com/metrodroid/metrodroid/wiki/FeliCa
 */
data class UnparsedTransitSystem(
    val systemCode: Int,
    val displayName: String
)

object UnparsedTransitSystems {
    private val systems = listOf(
        UnparsedTransitSystem(0x8157, "CI-CA／らんでんカード候補"),
        UnparsedTransitSystem(0x8194, "Rapica"),
        UnparsedTransitSystem(0x832C, "えこまいか／パスカ"),
        UnparsedTransitSystem(0x862C, "itappy"),
        UnparsedTransitSystem(0x8B43, "NORUCA"),
        UnparsedTransitSystem(0x8B98, "らんでんカード")
    )

    @JvmStatic
    fun fromSystemCode(systemCode: ByteArray?): UnparsedTransitSystem? {
        if (systemCode?.size != 2) return null
        val code = ((systemCode[0].toInt() and 0xff) shl 8) or
            (systemCode[1].toInt() and 0xff)
        return systems.firstOrNull { it.systemCode == code }
    }
}
