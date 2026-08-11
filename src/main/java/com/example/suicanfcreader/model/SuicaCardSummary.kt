package com.example.suicanfcreader.model

data class SuicaCardSummary(
    val cardId: String,
    val title: String,
    val latestRecord: Card,
    val recordCount: Int
)
