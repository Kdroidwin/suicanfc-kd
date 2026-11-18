package com.example.suicanfcreader.security

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF

/**
 * Validates that an incoming intent is a supported NFC dispatch intent and
 * carries a usable FeliCa tag. The actual tag read remains the authenticity
 * boundary, but this rejects malformed or unrelated intents before any read.
 */
object NfcIntentValidator {
    private const val FELICA_ID_LENGTH = 8

    private val NFC_ACTIONS = setOf(
        NfcAdapter.ACTION_TAG_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_NDEF_DISCOVERED
    )

    fun validate(context: Context, intent: Intent?): Tag? {
        val candidate = intent ?: return null
        if (candidate.action !in NFC_ACTIONS) return null

        val componentPackage = candidate.component?.packageName
        if (componentPackage != null && componentPackage != context.packageName) return null

        @Suppress("DEPRECATION")
        val tag = candidate.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return null
        val id = tag.id ?: return null
        if (id.size != FELICA_ID_LENGTH) return null
        if (tag.techList.none { it == NfcF::class.java.name }) return null

        return runCatching { NfcF.get(tag) }.getOrNull()?.let { tag }
    }
}
