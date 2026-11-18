package com.example.suicanfcreader.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    const val MIN_PASSWORD_LENGTH = 8

    private const val HEADER = "SUICANFC-KD-BACKUP"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_CIPHERTEXT_BYTES = 4 * 1024 * 1024

    fun isEncryptedBackup(raw: String): Boolean =
        raw.lineSequence().firstOrNull()?.trim() == HEADER

    fun encrypt(plaintext: String, password: CharArray): String {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            require(ciphertext.size <= MAX_CIPHERTEXT_BYTES) { "Backup is too large" }
            return buildString {
                appendLine(HEADER)
                appendLine(VERSION)
                appendLine(KDF)
                appendLine(ITERATIONS)
                appendLine(Base64.getEncoder().encodeToString(salt))
                appendLine(Base64.getEncoder().encodeToString(iv))
                append(Base64.getEncoder().encodeToString(ciphertext))
            }
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(envelope: String, password: CharArray): String {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        val lines = envelope.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.size == 7) { "Invalid backup envelope" }
        require(lines[0] == HEADER) { "Invalid backup header" }
        require(lines[1].toIntOrNull() == VERSION) { "Unsupported backup envelope version" }
        require(lines[2] == KDF) { "Unsupported backup KDF" }
        val iterations = lines[3].toIntOrNull()
            ?.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS }
            ?: error("Invalid backup KDF iterations")
        val salt = decode(lines[4], SALT_BYTES, SALT_BYTES)
        val iv = decode(lines[5], IV_BYTES, IV_BYTES)
        val ciphertext = decode(lines[6], TAG_BITS / 8, MAX_CIPHERTEXT_BYTES)
        val key = deriveKey(password, salt, iterations)
        try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
                .also { require(it.size == KEY_BITS / 8) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun decode(value: String, minBytes: Int, maxBytes: Int): ByteArray {
        require(value.isNotBlank()) { "Missing backup field" }
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size in minBytes..maxBytes) { "Invalid backup field size" }
        return bytes
    }
}
