package com.example.suicanfcreader.navigation

import com.example.suicanfcreader.R


enum class Screen(val route: String, val titleId: Int, val hasMenuItem: Boolean = true) {
    TopScreen("top_screen", R.string.top_screen, false),
    Stats("stats", R.string.top_screen, false),
    Settings("settings", R.string.top_screen, false)
}
