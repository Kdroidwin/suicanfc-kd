package com.example.suicanfcreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvEscaperTest {
    @Test
    fun neutralizesFormulaPrefixes() {
        assertEquals("\"'=1+1\"", CsvEscaper.escape("=1+1"))
        assertEquals("\"'+SUM(A1)\"", CsvEscaper.escape("+SUM(A1)"))
        assertEquals("\"'@cmd\"", CsvEscaper.escape("@cmd"))
        assertEquals("\"'-1+1\"", CsvEscaper.escape("-1+1"))
        assertEquals("\"'\t=1+1\"", CsvEscaper.escape("\t=1+1"))
        assertEquals("\"'  =1+1\"", CsvEscaper.escape("  =1+1"))
    }

    @Test
    fun keepsPlainNegativeNumbers() {
        assertEquals("\"-123\"", CsvEscaper.escape("-123"))
        assertEquals("\"-123.45\"", CsvEscaper.escape("-123.45"))
        assertEquals("\" -123\"", CsvEscaper.escape(" -123"))
    }

    @Test
    fun doublesQuotes() {
        assertEquals("\"a\"\"b\"", CsvEscaper.escape("a\"b"))
    }

    @Test
    fun handlesNullAndEmpty() {
        assertEquals("\"\"", CsvEscaper.escape(null))
        assertEquals("\"\"", CsvEscaper.escape(""))
    }
}
