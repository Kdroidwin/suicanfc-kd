package com.example.suicanfcreader.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileGuardTest {
    @Test
    fun rejectsZipAndGzipHeaders() {
        assertTrue(BackupFileGuard.isArchive(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertTrue(BackupFileGuard.isArchive(byteArrayOf(0x50, 0x4B, 0x05, 0x06)))
        assertTrue(BackupFileGuard.isArchive(byteArrayOf(0x50, 0x4B, 0x07, 0x08)))
        assertTrue(BackupFileGuard.isArchive(byteArrayOf(0x1F, 0x8B.toByte())))
    }

    @Test
    fun rejectsOtherArchiveHeaders() {
        assertTrue(BackupFileGuard.isArchive(byteArrayOf(0x52, 0x61, 0x72, 0x21)))
        assertTrue(
            BackupFileGuard.isArchive(
                byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
            )
        )
    }

    @Test
    fun acceptsJsonAndShortHeaders() {
        assertFalse(BackupFileGuard.isArchive("{\"schema\":2}".toByteArray()))
        assertFalse(BackupFileGuard.isArchive(byteArrayOf(0x50)))
        assertFalse(BackupFileGuard.isArchive(ByteArray(0)))
    }
}
