package com.example.suicanfcreader.util

object BackupFileGuard {
    private val ARCHIVE_SIGNATURES = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04), // ZIP local file
        byteArrayOf(0x50, 0x4B, 0x05, 0x06), // ZIP empty archive
        byteArrayOf(0x50, 0x4B, 0x07, 0x08), // ZIP spanned archive
        byteArrayOf(0x1F, 0x8B.toByte()), // gzip
        byteArrayOf(0x52, 0x61, 0x72, 0x21), // RAR
        byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C) // 7z
    )

    fun isArchive(header: ByteArray): Boolean =
        ARCHIVE_SIGNATURES.any { signature ->
            header.size >= signature.size &&
                signature.indices.all { index -> header[index] == signature[index] }
        }
}
