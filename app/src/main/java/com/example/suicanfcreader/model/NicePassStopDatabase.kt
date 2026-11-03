package com.example.suicanfcreader.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate

data class NicePassStop(
    val name: String,
    val operatorName: String?,
    val vehicle: String?
)

/** Read-only, card-specific lookup for the NicePass stop database supplied in the APK. */
object NicePassStopDatabase {
    private const val ASSET_NAME = "NicePassStops.db"
    private const val INTERNAL_NAME = "nicepass_stops.db"
    private const val MAX_DATABASE_BYTES = 2L * 1024L * 1024L
    private const val MAX_CACHE_ENTRIES = 2_000

    @Volatile
    private var openedDatabase: SQLiteDatabase? = null
    private val cache = LinkedHashMap<String, NicePassStop?>()

    fun find(context: Context, stopCode: Int, date: LocalDate): NicePassStop? {
        if (stopCode !in 1..0xFFFFF) return null
        val dateValue = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
        val key = "$stopCode:$dateValue"
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }

        val result = runCatching {
            val db = database(context.applicationContext) ?: return@runCatching null
            db.rawQuery(
                "SELECT DISTINCT name, enterprise1, enterprise2, vehicle FROM nicepass " +
                    "WHERE _id = ? AND idx >= 0 AND start <= ? AND end >= ? " +
                    "AND name IS NOT NULL AND trim(name) <> ''",
                arrayOf("%06d".format(java.util.Locale.US, stopCode), dateValue.toString(), dateValue.toString())
            ).use { cursor ->
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    rows += listOf(
                        cursor.getString(0).orEmpty().trim(),
                        cursor.getString(1).orEmpty().trim(),
                        cursor.getString(2).orEmpty().trim(),
                        cursor.getString(3).orEmpty().trim()
                    )
                }
                val names = rows.map { it[0] }.distinct()
                if (names.size != 1) {
                    null
                } else {
                    val operators = rows.map { listOf(it[1], it[2]).filter(String::isNotBlank).joinToString(" / ") }
                        .filter(String::isNotBlank)
                        .distinct()
                    val vehicleTypes = rows.map { it[3] }.filter(String::isNotBlank).distinct()
                    NicePassStop(names.single(), operators.singleOrNull(), vehicleTypes.singleOrNull())
                }
            }
        }.getOrNull()

        synchronized(cache) {
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            cache[key] = result
        }
        return result
    }

    private fun database(context: Context): SQLiteDatabase? {
        openedDatabase?.takeIf { it.isOpen }?.let { return it }
        return synchronized(this) {
            openedDatabase?.takeIf { it.isOpen } ?: runCatching {
                val file = installPrivateCopy(context) ?: return@runCatching null
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    .also { db ->
                        db.rawQuery(
                            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'nicepass'",
                            null
                        ).use { cursor ->
                            if (!cursor.moveToFirst()) throw IOException("NicePass stop table missing")
                        }
                    }
                    .also { openedDatabase = it }
            }.getOrNull()
        }
    }

    private fun installPrivateCopy(context: Context): File? {
        val destination = context.getDatabasePath(INTERNAL_NAME)
        if (isValidDatabase(destination)) return destination
        val parent = destination.parentFile ?: return null
        if (!parent.exists() && !parent.mkdirs()) return null
        val temporary = File(parent, "$INTERNAL_NAME.tmp")
        temporary.delete()

        return try {
            context.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_DATABASE_BYTES) throw IOException("NicePass database too large")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!isValidDatabase(temporary)) throw IOException("Invalid NicePass database asset")
            if (destination.exists() && !destination.delete()) throw IOException("Could not replace invalid NicePass database")
            if (!temporary.renameTo(destination)) throw IOException("Could not install NicePass database")
            destination.takeIf(::isValidDatabase)
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private fun isValidDatabase(file: File): Boolean {
        if (!file.isFile || file.length() !in SQLITE_HEADER.size.toLong()..MAX_DATABASE_BYTES) return false
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
