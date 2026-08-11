package com.example.suicanfcreader.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    private const val LEGACY_NAME = "suica_reader_history"
    private const val SECURE_NAME = "suica_reader_history_secure"
    private const val MIGRATION_COMPLETE = "secure_migration_complete"
    private const val LEGACY_CLEANUP_COMPLETE = "legacy_cleanup_complete"

    fun open(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            appContext,
            SECURE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateLegacyPreferences(appContext, encrypted)
        return encrypted
    }

    private fun migrateLegacyPreferences(context: Context, encrypted: SharedPreferences) {
        synchronized(this) {
            val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
            if (!encrypted.getBoolean(MIGRATION_COMPLETE, false)) {
                val editor = encrypted.edit()
                legacy.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                editor.putBoolean(MIGRATION_COMPLETE, true)
                editor.putBoolean(LEGACY_CLEANUP_COMPLETE, false)
                check(editor.commit()) { "Encrypted preference migration failed" }
            }
            if (!encrypted.getBoolean(LEGACY_CLEANUP_COMPLETE, false)) {
                check(legacy.edit().clear().commit()) { "Legacy preference cleanup failed" }
                check(encrypted.edit().putBoolean(LEGACY_CLEANUP_COMPLETE, true).commit()) {
                    "Encrypted preference migration cleanup marker failed"
                }
            }
        }
    }
}
