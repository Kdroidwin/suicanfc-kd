package com.example.suicanfcreader.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.concurrent.atomic.AtomicLong

object SensitiveClipboard {
    private const val CLEAR_DELAY_MILLIS = 60_000L
    private const val MASKED_LABEL = "*****"
    private val copySequence = AtomicLong()

    fun copy(context: Context, value: String) {
        val appContext = context.applicationContext ?: context
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
        val token = copySequence.incrementAndGet()
        // Keep the pasted payload intact while hiding its label from clipboard previews.
        val clip = ClipData.newPlainText(MASKED_LABEL, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = (clip.description.extras ?: PersistableBundle()).apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).postDelayed({
            val current = clipboard.primaryClip
            val currentText = current?.getItemAt(0)?.coerceToText(appContext)?.toString()
            if (copySequence.get() == token &&
                clipboard.primaryClipDescription?.label?.toString() == MASKED_LABEL &&
                currentText == value
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    // clearPrimaryClip() is only available on API 28+.
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }, CLEAR_DELAY_MILLIS)
    }
}
