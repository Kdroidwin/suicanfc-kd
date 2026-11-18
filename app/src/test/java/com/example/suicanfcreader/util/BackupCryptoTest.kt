package com.example.suicanfcreader.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val password = "correct horse battery".toCharArray()

    @Test
    fun roundTripsEncryptedBackup() {
        val plaintext = "{\"schema\":2,\"history\":[]}"
        val envelope = BackupCrypto.encrypt(plaintext, password)

        assertTrue(BackupCrypto.isEncryptedBackup(envelope))
        assertTrue(envelope.startsWith("SUICANFC-KD-BACKUP"))
        assertTrue(BackupCrypto.decrypt(envelope, password) == plaintext)
    }

    @Test
    fun rejectsWrongPassword() {
        val envelope = BackupCrypto.encrypt("{\"schema\":2}", password)
        try {
            BackupCrypto.decrypt(envelope, "wrong password".toCharArray())
            fail("Wrong password must fail")
        } catch (_: Exception) {
            // Expected: GCM authentication fails.
        }
    }

    @Test
    fun rejectsTamperedCiphertext() {
        val envelope = BackupCrypto.encrypt("{\"schema\":2}", password)
        val lines = envelope.lines()
        val ciphertext = lines[6]
        val index = ciphertext.length / 2
        val replacement = if (ciphertext[index] == 'A') 'B' else 'A'
        val tamperedCiphertext = ciphertext.substring(0, index) + replacement + ciphertext.substring(index + 1)
        val tampered = (lines.take(6) + tamperedCiphertext).joinToString("\n")
        try {
            BackupCrypto.decrypt(tampered, password)
            fail("Tampered ciphertext must fail")
        } catch (_: Exception) {
            // Expected: GCM authentication fails.
        }
    }

    @Test
    fun distinguishesLegacyPlaintext() {
        assertFalse(BackupCrypto.isEncryptedBackup("{\"schema\":2}"))
    }
}
