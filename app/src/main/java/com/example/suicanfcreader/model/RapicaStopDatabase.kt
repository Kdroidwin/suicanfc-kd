package com.example.suicanfcreader.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

data class RapicaStop(
    val name: String,
    val routeName: String?
)

/** Read-only access to the stop table extracted from the supplied AllNFC APK. */
object RapicaStopDatabase {
    private const val ASSET_NAME = "RapicaStops.db"
    private const val INTERNAL_NAME = "rapica_stops.db"
    private const val MAX_DATABASE_BYTES = 16L * 1024L * 1024L
    private const val MAX_CACHE_ENTRIES = 1_000

    @Volatile
    private var openedDatabase: SQLiteDatabase? = null
    private val cache = LinkedHashMap<String, RapicaStop?>()

    fun find(context: Context, companyCode: Int, stopCode: Int, date: LocalDate): RapicaStop? {
        if (stopCode !in 1..0xFFFFFF) return null
        val operatorId = ((companyCode shr 4) and 0x0f).takeIf { it in 1..5 }?.toString() ?: return null
        val dateValue = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
        val code = stopCode.toString(16).uppercase(Locale.US)
        val key = "$operatorId:$code:$dateValue"

        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }

        val found = runCatching {
            val db = database(context.applicationContext) ?: return@runCatching null
            db.rawQuery(
                "SELECT DISTINCT name, line, keito FROM rapica " +
                    "WHERE entid = ? AND upper(_id) = ? AND idx = 0 " +
                    "AND start <= ? AND end >= ? AND name IS NOT NULL AND trim(name) <> ''",
                arrayOf(operatorId, code, dateValue.toString(), dateValue.toString())
            ).use { cursor ->
                val rows = mutableListOf<Triple<String, String, String>>()
                while (cursor.moveToNext()) {
                    rows += Triple(
                        cursor.getString(0).orEmpty().trim(),
                        cursor.getString(1).orEmpty().trim(),
                        cursor.getString(2).orEmpty().trim()
                    )
                }
                val names = rows.map { it.first }.filter { it.isNotBlank() }.distinct()
                if (names.size != 1) {
                    null
                } else {
                    val routeLabels = rows.map { listOf(it.second, it.third).filter(String::isNotBlank).joinToString(" / ") }
                        .filter(String::isNotBlank)
                        .distinct()
                    RapicaStop(
                        name = names.single(),
                        routeName = routeLabels.singleOrNull()
                    )
                }
            }
        }.getOrNull()

        synchronized(cache) {
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            cache[key] = found
        }
        return found
    }

    private fun database(context: Context): SQLiteDatabase? {
        openedDatabase?.takeIf { it.isOpen }?.let { return it }
        return synchronized(this) {
            openedDatabase?.takeIf { it.isOpen } ?: runCatching {
                val file = ensureInternalCopy(context) ?: return@runCatching null
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .also { db ->
                        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='rapica'", null)
                            .use { cursor -> if (!cursor.moveToFirst()) throw IOException("RapiCa stop table missing") }
                    }
                    .also { openedDatabase = it }
            }.getOrNull()
        }
    }

    private fun ensureInternalCopy(context: Context): File? {
        val destination = context.getDatabasePath(INTERNAL_NAME)
        if (isValidDatabaseFile(destination)) return destination

        val parent = destination.parentFile ?: return null
        if (!parent.exists() && !parent.mkdirs()) return null
        val temporary = File(parent, "$INTERNAL_NAME.tmp")
        temporary.delete()

        try {
            context.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_DATABASE_BYTES) throw IOException("RapiCa database too large")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!isValidDatabaseFile(temporary)) throw IOException("Invalid RapiCa database asset")
            if (destination.exists() && !isValidDatabaseFile(destination) && !destination.delete()) {
                throw IOException("Could not replace invalid RapiCa database")
            }
            if (!temporary.renameTo(destination)) {
                if (!isValidDatabaseFile(destination)) throw IOException("Could not install RapiCa database")
                temporary.delete()
            }
            return destination.takeIf(::isValidDatabaseFile)
        } catch (_: Exception) {
            temporary.delete()
            return null
        }
    }

    private fun isValidDatabaseFile(file: File): Boolean {
        if (!file.isFile || file.length() !in 16..MAX_DATABASE_BYTES) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(SQLITE_HEADER.size)
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count < 0) return@use false
                    offset += count
                }
                header.contentEquals(SQLITE_HEADER)
            }
        }.getOrDefault(false)
    }

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
}
