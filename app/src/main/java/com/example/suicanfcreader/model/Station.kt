package com.example.suicanfcreader.model

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

data class Station(
    val regionCode: Int = -1,
    var lineName: String = "",
    var stationName: String = "",
    var company: String = ""
) {
    companion object {
        private const val TAG = "Station"

        @Volatile
        private var stationCache: StationCache? = null
        @Volatile
        private var busStopCache: StationCache? = null

        fun getStation(context: Context, regionCode: Int, lineCode: Int, stationCode: Int): Station? {
            return resolvePair(
                context = context,
                regionCode = regionCode,
                inLineCode = lineCode,
                inStationCode = stationCode,
                outLineCode = 0,
                outStationCode = 0
            ).first
        }

        fun getBusStop(context: Context, lineCode: Int, stationCode: Int): Station? {
            if (lineCode == 0 && stationCode == 0) return null
            val cache = loadBusStops(context)
            return cache.candidates(lineCode, stationCode).preferred()
                ?: cache.candidates(lineCode, 0).preferred()
                ?: cache.candidates(lineCode and 0xff, stationCode and 0xff).preferred()
        }

        fun resolvePair(
            context: Context,
            regionCode: Int,
            inLineCode: Int,
            inStationCode: Int,
            outLineCode: Int,
            outStationCode: Int
        ): Pair<Station?, Station?> {
            val cache = loadStations(context)
            val areaCode = areaCodeFromRegion(regionCode)
            return cache.railCandidate(areaCode, inLineCode, inStationCode) to
                cache.railCandidate(areaCode, outLineCode, outStationCode)
        }

        private fun loadStations(context: Context): StationCache {
            stationCache?.let { return it }
            return synchronized(this) {
                stationCache ?: readStationCsv(context).also { stationCache = it }
            }
        }

        private fun loadBusStops(context: Context): StationCache {
            busStopCache?.let { return it }
            return synchronized(this) {
                busStopCache ?: readBusStopCsv(context).also { busStopCache = it }
            }
        }

        private fun readStationCsv(context: Context): StationCache {
            val byAreaLineStation = linkedMapOf<String, Station>()

            try {
                context.assets.open("StationCode.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { br ->
                        br.lineSequence().forEach { line ->
                            val tokens = line.split(",").map { it.trim() }
                            if (tokens.size >= 6) {
                                val region = tokens[0].toIntOrNull() ?: return@forEach
                                val lineCode = tokens[1].toIntOrNull() ?: return@forEach
                                val stationCode = tokens[2].toIntOrNull() ?: return@forEach
                                if (lineCode == 0 && stationCode == 0) return@forEach

                                val station = Station(
                                    regionCode = region,
                                    company = tokens[3],
                                    lineName = tokens[4],
                                    stationName = normalizeStationName(tokens[3], tokens[5])
                                )
                                byAreaLineStation[areaLineStationKey(region, lineCode, stationCode)] = station
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                Log.w(TAG, "Station database could not be loaded")
            }

            return StationCache(byAreaLineStation = byAreaLineStation)
        }

        private fun readBusStopCsv(context: Context): StationCache {
            val byAreaLineStation = linkedMapOf<String, Station>()

            try {
                context.assets.open("BusCode.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { br ->
                        br.lineSequence().forEach { line ->
                            val tokens = line.split(",").map { it.trim().trim('"') }
                            if (tokens.size >= 5) {
                                val lineCode = tokens[0].toIntOrNull(16) ?: return@forEach
                                val stationCode = tokens[1].toIntOrNull(16) ?: return@forEach
                                if (lineCode == 0 && stationCode == 0) return@forEach

                                val station = Station(
                                    regionCode = 0,
                                    company = tokens[2],
                                    lineName = tokens[3],
                                    stationName = tokens[4]
                                )
                                byAreaLineStation[areaLineStationKey(0, lineCode, stationCode)] = station
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                Log.w(TAG, "Bus stop database could not be loaded")
            }

            return StationCache(byAreaLineStation = byAreaLineStation)
        }

        private fun StationCache.railCandidate(areaCode: Int, lineCode: Int, stationCode: Int): Station? {
            if (lineCode == 0 && stationCode == 0) return null
            return byAreaLineStation[areaLineStationKey(areaCode, lineCode, stationCode)]
        }

        private fun StationCache.candidates(lineCode: Int, stationCode: Int): List<Station> {
            if (lineCode == 0 && stationCode == 0) return emptyList()
            return listOfNotNull(byAreaLineStation[areaLineStationKey(0, lineCode, stationCode)])
        }

        private fun List<Station>.preferred(): Station? {
            return firstOrNull()
        }

        private fun areaCodeFromRegion(regionCode: Int): Int =
            if (regionCode > 0x3f) (regionCode shr 6) and 0xff else regionCode

        private fun normalizeStationName(company: String, stationName: String): String {
            val hankyu = "\u962a\u6025\u96fb\u9244"
            val hanshin = "\u962a\u795e\u96fb\u6c17\u9244"
            val umeda = "\u6885\u7530"
            val osakaUmeda = "\u5927\u962a\u6885\u7530"
            val sannomiya = "\u4e09\u5bae"
            val kobeSannomiya = "\u795e\u6238\u4e09\u5bae"
            return when {
                (company == hankyu || company == hanshin) && stationName == umeda -> osakaUmeda
                (company == hankyu || company == hanshin) && stationName == sannomiya -> kobeSannomiya
                else -> stationName
            }
        }

        private fun areaLineStationKey(areaCode: Int, lineCode: Int, stationCode: Int): String =
            "$areaCode:$lineCode:$stationCode"

        private data class StationCache(
            val byAreaLineStation: Map<String, Station>
        )
    }
}
