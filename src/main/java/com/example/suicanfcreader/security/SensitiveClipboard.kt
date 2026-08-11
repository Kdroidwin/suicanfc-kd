package com.example.suicanfcreader.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

object SensitiveClipboard {
    private const val CLEAR_DELAY_MILLIS = 60_000L
    private const val MASKED_LABEL = "*****"

    fun copy(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        // Keep the pasted payload intact, while hiding its label from clipboard previews.
        val clip = ClipData.newPlainText(MASKED_LABEL, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = (clip.description.extras ?: android.os.PersistableBundle()).apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).postDelayed({
            val current = clipboard.primaryClip
            val currentText = current?.getItemAt(0)?.coerceToText(context)?.toString()
            if (clipboard.primaryClipDescription?.label == MASKED_LABEL && currentText == value) {
                clipboard.clearPrimaryClip()
            }
        }, CLEAR_DELAY_MILLIS)
    }
}
