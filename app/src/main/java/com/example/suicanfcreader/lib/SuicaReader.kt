package com.example.suicanfcreader.lib

import java.io.ByteArrayOutputStream
import java.io.IOException

class SuicaReader {
    var termId = 0
    var procId = 0
    var year = 0
    var month = 0
    var day = 0
    var kind = ""
    var remain = 0
    var seqNo = 0
    var regionCode = 0
    var inStation = 0
    var inLine = 0
    var outStation = 0
    var outLine = 0
    var busLine = 0
    var busStop = 0
    var device = ""
    var action = ""

    private fun init(res: ByteArray, off: Int) {
        termId = unsigned(res[off])
        procId = unsigned(res[off + 1])

        val dateBits = intAt(res, off, 4, 5)
        year = (dateBits shr 9) and 0x07f
        month = (dateBits shr 5) and 0x00f
        day = dateBits and 0x01f

        kind = when {
            isCharge(procId) -> "チャージ"
            isShopping(procId) -> "物販"
            isBus(procId) -> "バス"
            unsigned(res[off + 6]) < 0x80 -> "JR"
            else -> "公営/私鉄"
        }

        inLine = intAt(res, off, 6)
        inStation = intAt(res, off, 7)
        outLine = intAt(res, off, 8)
        outStation = intAt(res, off, 9)
        busLine = intAt(res, off, 6, 7)
        busStop = intAt(res, off, 8, 9)
        remain = intAt(res, off, 11, 10)
        seqNo = intAt(res, off, 12, 13, 14)
        regionCode = unsigned(res[off + 15])
        device = deviceList[termId] ?: "端末不明"
        action = actionList[procId] ?: kind
    }

    private fun intAt(res: ByteArray, off: Int, vararg indexes: Int): Int {
        var value = 0
        for (index in indexes) {
            value = (value shl 8) + unsigned(res[off + index])
        }
        return value
    }

    fun isStationRecord(): Boolean =
        !isCharge(procId) && !isShopping(procId) && !isBus(procId) &&
            (inLine != 0 || inStation != 0 || outLine != 0 || outStation != 0)

    fun isChargeRecord(): Boolean = isCharge(procId)

    fun isShoppingRecord(): Boolean = isShopping(procId)

    fun isBusRecord(): Boolean = isBus(procId)

    fun hasPlausibleHistoryValues(): Boolean {
        if (year !in 0..99 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return false
        if (remain !in 0..MAX_TRANSIT_BALANCE) return false
        return inLine in BYTE_VALUE_RANGE && inStation in BYTE_VALUE_RANGE &&
            outLine in BYTE_VALUE_RANGE && outStation in BYTE_VALUE_RANGE &&
            regionCode in BYTE_VALUE_RANGE && busLine in BUS_CODE_RANGE && busStop in BUS_CODE_RANGE
    }

    private fun isCharge(value: Int): Boolean = value == 2 || value == 31 || value == 72 || value == 73

    private fun isShopping(value: Int): Boolean = value == 70 || value == 74 || value == 75 || value == 198 || value == 203

    private fun isBus(value: Int): Boolean = value == 13 || value == 15 || value == 31 || value == 35

    companion object {
        private val deviceList = mapOf(
            3 to "精算機", 4 to "携帯型端末", 5 to "車載端末", 7 to "券売機", 8 to "券売機",
            9 to "入金機", 18 to "券売機", 20 to "券売機等", 21 to "券売機等", 22 to "改札機",
            23 to "簡易改札機", 24 to "窓口端末", 25 to "窓口端末", 26 to "改札端末", 27 to "携帯電話",
            28 to "乗継精算機", 29 to "連絡改札機", 31 to "簡易入金機", 70 to "VIEW ALTTE",
            72 to "VIEW ALTTE", 199 to "物販端末", 200 to "自販機"
        )

        private val actionList = mapOf(
            1 to "運賃支払", 2 to "チャージ", 3 to "券購入", 4 to "精算", 5 to "入場精算",
            6 to "窓口処理", 7 to "新規発行", 8 to "控除", 13 to "バス", 15 to "バス", 17 to "再発行",
            19 to "支払", 20 to "入場オートチャージ", 21 to "出場オートチャージ", 31 to "バスチャージ",
            35 to "バス券購入", 70 to "物販", 72 to "特典チャージ", 73 to "レジ入金", 74 to "物販取消",
            75 to "入場物販", 132 to "他社精算", 133 to "他社入場精算", 198 to "現金併用物販",
            203 to "入場現金併用物販"
        )

        @JvmStatic
        fun parse(res: ByteArray?, off: Int): SuicaReader {
            require(res != null && off >= 0 && res.size - off >= 16) { "Invalid FeliCa history block" }
            return SuicaReader().also { it.init(res, off) }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readWithoutEncryption(idm: ByteArray, size: Int): ByteArray =
            readWithoutEncryption(idm, 0, size)

        @JvmStatic
        @Throws(IOException::class)
        fun readWithoutEncryption(idm: ByteArray, startBlock: Int, size: Int): ByteArray {
            if (idm.size != 8) throw IOException("Invalid FeliCa IDm")
            if (startBlock !in 0..255 || size !in 1..10 || startBlock + size > 256) {
                throw IOException("Invalid FeliCa block range")
            }
            return ByteArrayOutputStream(100).use { output ->
                output.write(0)
                output.write(0x06)
                output.write(idm)
                output.write(1)
                output.write(0x0f)
                output.write(0x09)
                output.write(size)
                repeat(size) { index ->
                    output.write(0x80)
                    output.write(startBlock + index)
                }
                output.toByteArray().also { it[0] = it.size.toByte() }
            }
        }

        private fun unsigned(value: Byte): Int = value.toInt() and 0x0ff

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private val BYTE_VALUE_RANGE = 0..0xff
        private val BUS_CODE_RANGE = 0..0xffff
        private const val MAX_TRANSIT_BALANCE = 20_000
    }
}
