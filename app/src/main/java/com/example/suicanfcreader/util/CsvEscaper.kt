package com.example.suicanfcreader.util

object CsvEscaper {
    fun escape(value: String?): String {
        val raw = value.orEmpty()
        // Spreadsheet apps evaluate cells beginning with these characters as formulas.
        // Keep plain negative numbers intact, but neutralize everything else.
        val trimmed = raw.trimStart()
        val first = trimmed.firstOrNull()
        val needsFormulaGuard = when {
            raw.firstOrNull() == '\t' || raw.firstOrNull() == '\r' -> true
            first == '=' || first == '+' || first == '@' -> true
            first == '-' -> !trimmed.isPlainNumber()
            else -> false
        }
        val safe = if (needsFormulaGuard) "'$raw" else raw
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun String.isPlainNumber(): Boolean {
        if (isEmpty()) return false
        var index = 0
        if (this[0] == '-') index++
        if (index == length) return false
        var digits = 0
        while (index < length && this[index].isDigit()) {
            index++
            digits++
        }
        if (digits == 0) return false
        if (index < length && this[index] == '.') {
            index++
            var fractionDigits = 0
            while (index < length && this[index].isDigit()) {
                index++
                fractionDigits++
            }
            if (fractionDigits == 0) return false
        }
        return index == length
    }
}
