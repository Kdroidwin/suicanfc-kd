package com.example.suicanfcreader.model

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/** Maps the terminal ID retained in a Suica SF-history shopping record to a known merchant. */
data class Merchant(
    val brand: String,
    val store: String,
    val detail: String
) {
    companion object {
        private const val TAG = "Merchant"
        private const val SUICA = "Suica"

        @Volatile
        private var cache: Map<String, Merchant>? = null
        @Volatile
        private var extendedCache: Map<String, Merchant>? = null

        fun getSuicaMerchant(context: Context, terminalId: Int, merchantId: Int): Merchant? {
            val terminalCode = "%02X".format(terminalId)
            val merchantCode = "%04X".format(merchantId)
            return load(context)["$SUICA:$terminalCode:$merchantCode"]
        }

        /**
         * Looks up the newer five-hex-digit merchant code format. Existing SF-history records
         * expose only the legacy four-digit value, so callers opt into this explicitly.
         */
        fun getSuicaMerchant(context: Context, terminalId: Int, merchantCode: String): Merchant? {
            val normalizedCode = merchantCode.uppercase()
            if (!FIVE_DIGIT_CODE.matches(normalizedCode)) return null
            return loadExtended(context)["$SUICA:${"%02X".format(terminalId)}:$normalizedCode"]
        }

        private fun load(context: Context): Map<String, Merchant> {
            cache?.let { return it }
            return synchronized(this) {
                cache ?: read(context, "MerchantCode.csv").also { cache = it }
            }
        }

        private fun loadExtended(context: Context): Map<String, Merchant> {
            extendedCache?.let { return it }
            return synchronized(this) {
                extendedCache ?: read(context, "MerchantCodeV4006.csv").also { extendedCache = it }
            }
        }

        private fun read(context: Context, assetName: String): Map<String, Merchant> =
            linkedMapOf<String, Merchant>().also { merchants ->
                try {
                    context.assets.open(assetName).use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            reader.lineSequence().forEach { row ->
                                val values = row.split(",").map(String::trim)
                                if (values.size >= 6 && values[0] == SUICA) {
                                    merchants["${values[0]}:${values[1]}:${values[2]}"] =
                                        Merchant(values[3], values[4], values[5])
                                }
                            }
                        }
                    }
                } catch (_: IOException) {
                    Log.w(TAG, "Merchant database $assetName could not be loaded")
                }
            }

        private val FIVE_DIGIT_CODE = Regex("[0-9A-F]{5}")
    }
}
