package com.example.suicanfcreader.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.suicanfcreader.storage.SecurePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class BalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    internal fun buildViews(context: Context): RemoteViews {
        val prefs = SecurePreferences.open(context)
        val privacyMode = prefs.getBoolean("widget_privacy_mode", true)
        if (privacyMode) {
            return RemoteViews("android", android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "交通系ICカード\n残高はアプリで確認してください")
                setTextColor(android.R.id.text1, Color.WHITE)
                setInt(
                    android.R.id.text1,
                    "setBackgroundColor",
                    prefs.getString("widget_background_color", "#000000").toColorInt()
                )
            }
        }
        val cards = runCatching {
            val array = JSONArray(prefs.getString("history", "[]") ?: "[]")
            List(array.length()) { index -> array.getJSONObject(index) }
        }.getOrDefault(emptyList())
        val aliases = runCatching {
            val obj = JSONObject(prefs.getString("card_aliases", "{}") ?: "{}")
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrDefault(emptyMap())
        val appTitle = prefs.getString("app_title", "SuicaNFC KD") ?: "SuicaNFC KD"
        val backgroundColor = prefs.getString("widget_background_color", "#000000").toColorInt()
        val latestByCard = cards.groupBy { it.optString("cardId", "legacy") }
            .mapValues { (_, records) -> records.maxByOrNull { it.optInt("number", 0) } }
            .filterValues { it != null }
        val totalSpending = cards.sumOf { record ->
            val amount = record.optString("amount").toIntOrNull() ?: 0
            if (amount < 0) -amount else 0
        }
        val movementCount = cards.count { record ->
            record.optString("inStation").isNotBlank() || record.optString("outStation").isNotBlank()
        }
        val lines = latestByCard.entries.take(3).joinToString("\n") { (cardId, record) ->
            val title = aliases[cardId] ?: cardId.maskForDisplay()
            "$title  ${record?.optString("balance").formatYen()}"
        }.ifBlank { "交通系ICカードをかざしてください" }
        val details = "支出 ${totalSpending.formatYen()} / 移動 ${movementCount}回 / 履歴 ${cards.size}件"
        val imageUri = prefs.getString("widget_background_image_uri", null)
        val image = imageUri?.let { createWidgetBitmap(context, it, backgroundColor, appTitle, lines, details) }

        if (image != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews("android", android.R.layout.activity_list_item).apply {
                setImageViewBitmap(android.R.id.icon, image)
                setViewLayoutWidth(android.R.id.icon, 320f, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(android.R.id.icon, 160f, TypedValue.COMPLEX_UNIT_DIP)
                setViewVisibility(android.R.id.text1, View.GONE)
            }
        }

        return RemoteViews("android", android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "$appTitle\n$lines\n$details")
            setTextColor(android.R.id.text1, Color.WHITE)
            setInt(android.R.id.text1, "setBackgroundColor", backgroundColor)
        }
    }

    private fun createWidgetBitmap(
        context: Context,
        uriText: String,
        backgroundColor: Int,
        title: String,
        lines: String,
        details: String
    ): Bitmap? = runCatching {
        val uri = Uri.parse(uriText)
        if (!isTrustedWidgetImageUri(context, uri)) return null
        val source = decodeBoundedBitmap(context, uri) ?: return null
        val width = 1200
        val height = 600
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)
        val scale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), null)
        canvas.drawColor(0x62000000)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        paint.textSize = 54f
        canvas.drawText(title, 48f, 78f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 44f
        var y = 166f
        lines.lineSequence().forEach { line ->
            canvas.drawText(line, 48f, y, paint)
            y += 58f
        }
        paint.textSize = 32f
        paint.color = 0xFFE0E8E5.toInt()
        canvas.drawText(details, 48f, height - 44f, paint)
        source.recycle()
        output
    }.getOrNull()

    private fun decodeBoundedBitmap(context: Context, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sampleSize = 1
        while (max(options.outWidth / sampleSize, options.outHeight / sampleSize) > 1600) sampleSize *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }

    private fun isTrustedWidgetImageUri(context: Context, uri: Uri): Boolean = runCatching {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank())
        require(context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        })
        require(context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)?.startsWith("image/") == true)
        val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(length < 0 || length <= MAX_WIDGET_IMAGE_BYTES)
        true
    }.getOrDefault(false)

    private fun String?.formatYen(): String {
        val value = this?.toIntOrNull() ?: return "¥--"
        return String.format(Locale.JAPAN, "¥%,d", value)
    }

    private fun Int.formatYen(): String = String.format(Locale.JAPAN, "¥%,d", this)

    private fun String.maskForDisplay(): String =
        if (length <= 4) this else "****${takeLast(4)}"

    private fun String?.toColorInt(): Int {
        val value = this?.trim()?.removePrefix("#")?.toLongOrNull(16) ?: return 0xFF000000.toInt()
        return (0xFF000000L or value).toInt()
    }

    companion object {
        private const val MAX_WIDGET_IMAGE_BYTES = 20L * 1024L * 1024L

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BalanceWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isEmpty()) return
            val views = BalanceWidgetProvider().buildViews(context)
            widgetIds.forEach { widgetId -> manager.updateAppWidget(widgetId, views) }
        }
    }
}
