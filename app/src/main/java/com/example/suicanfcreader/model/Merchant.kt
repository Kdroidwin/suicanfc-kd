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

        fun getSuicaMerchant(context: Context, terminalId: Int, merchantId: Int): Merchant? {
            val terminalCode = "%02X".format(terminalId)
            val merchantCode = "%04X".format(merchantId)
            return load(context)["$SUICA:$terminalCode:$merchantCode"]
        }

        private fun load(context: Context): Map<String, Merchant> {
            cache?.let { return it }
            return synchronized(this) {
                cache ?: linkedMapOf<String, Merchant>().also { merchants ->
                    try {
                        context.assets.open("MerchantCode.csv").use { inputStream ->
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
                        Log.w(TAG, "Merchant database could not be loaded")
                    }
                }.also { cache = it }
            }
        }
    }
}
