package com.example.suicanfcreader.model

enum class AppThemeMode(val label: String) {
    AMOLED("AMOLED Black"),
    DARK("ダーク"),
    WHITE("ライト"),
    CUSTOM_IMAGE("画像テーマ");

    companion object {
        fun fromName(name: String?): AppThemeMode {
            return entries.firstOrNull { it.name == name } ?: AMOLED
        }
    }
}
