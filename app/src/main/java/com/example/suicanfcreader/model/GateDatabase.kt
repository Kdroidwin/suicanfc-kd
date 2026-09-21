package com.example.suicanfcreader.model

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/** Offline lookup table for station ticket-gate codes. */
data class TicketGate(
    val areaCode: Int,
    val lineCode: Int,
    val stationCode: Int,
    val gateCode: Int,
    val company: String,
    val lineName: String,
    val stationName: String,
    val gateName: String,
    val gateNumber: String,
    val status: String
)

object GateDatabase {
    private const val TAG = "GateDatabase"

    @Volatile
    private var cache: Map<String, TicketGate>? = null

    /**
     * Looks up a gate only when a caller has an actual gate code. SF history records do not
     * contain one, so this method intentionally is not used by the existing history parser.
     */
    fun get(
        context: Context,
        regionCode: Int,
        lineCode: Int,
        stationCode: Int,
        gateCode: Int
    ): TicketGate? = load(context)[key(areaCodeFromRegion(regionCode), lineCode, stationCode, gateCode)]

    private fun load(context: Context): Map<String, TicketGate> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: linkedMapOf<String, TicketGate>().also { gates ->
                try {
                    context.assets.open("GateCode.csv").use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            reader.lineSequence().forEach lineLoop@ { row ->
                                val values = row.split(",").map(String::trim)
                                if (values.size < 10) return@lineLoop
                                val area = values[0].toIntOrNull() ?: return@lineLoop
                                val line = values[1].toIntOrNull() ?: return@lineLoop
                                val station = values[2].toIntOrNull() ?: return@lineLoop
                                val gate = values[3].toIntOrNull(16) ?: return@lineLoop
                                gates[key(area, line, station, gate)] = TicketGate(
                                    areaCode = area,
                                    lineCode = line,
                                    stationCode = station,
                                    gateCode = gate,
                                    company = values[4],
                                    lineName = values[5],
                                    stationName = values[6],
                                    gateName = values[7],
                                    gateNumber = values[8],
                                    status = values[9]
                                )
                            }
                        }
                    }
                } catch (_: IOException) {
                    Log.w(TAG, "Gate database could not be loaded")
                }
            }.also { cache = it }
        }
    }

    private fun areaCodeFromRegion(regionCode: Int): Int =
        if (regionCode > 0x3f) (regionCode shr 6) and 0xff else regionCode

    private fun key(areaCode: Int, lineCode: Int, stationCode: Int, gateCode: Int): String =
        "$areaCode:$lineCode:$stationCode:$gateCode"
}
