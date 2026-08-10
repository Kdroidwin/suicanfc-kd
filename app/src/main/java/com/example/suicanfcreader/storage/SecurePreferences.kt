package com.example.suicanfcreader.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    private const val LEGACY_NAME = "suica_reader_history"
    private const val SECURE_NAME = "suica_reader_history_secure"
    private const val MIGRATION_COMPLETE = "secure_migration_complete"

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
        if (encrypted.getBoolean(MIGRATION_COMPLETE, false)) return
        synchronized(this) {
            if (encrypted.getBoolean(MIGRATION_COMPLETE, false)) return
            val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
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
            check(editor.commit()) { "Encrypted preference migration failed" }
            check(legacy.edit().clear().commit()) { "Legacy preference cleanup failed" }
        }
    }
}
