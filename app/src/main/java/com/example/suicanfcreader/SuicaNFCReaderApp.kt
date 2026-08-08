package com.example.suicanfcreader

import android.app.Activity
import android.graphics.Color.TRANSPARENT
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.suicanfcreader.navigation.Screen
import com.example.suicanfcreader.model.AppThemeMode
import com.example.suicanfcreader.navigation.SuicaNFCReaderNavigation
import com.example.suicanfcreader.ui.theme.SuicaNFCReaderTheme
import com.example.suicanfcreader.viewModel.TopScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuicaNFCReaderApp(viewModel: TopScreenViewModel) {
    val themeMode = viewModel.themeMode.observeAsState(AppThemeMode.AMOLED)
    val accentColorHex = viewModel.accentColorHex.observeAsState("#8AD7C8")
    val appTitle = viewModel.appTitle.observeAsState("SuicaNFC KD")
    val useSearchIcon = viewModel.useSearchIcon.observeAsState(true)
    val showPaletteIcon = viewModel.showPaletteIcon.observeAsState(true)
    val showMoreMenu = viewModel.showMoreMenu.observeAsState(true)
    val useModernUi = viewModel.useModernUi.observeAsState(true)
    val showBottomTabLabels = viewModel.showBottomTabLabels.observeAsState(true)

    SuicaNFCReaderTheme(themeMode = themeMode.value, accentColorHex = accentColorHex.value) {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        ApplySystemBars(themeMode.value)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = appTitle.value,
                            modifier = Modifier.clickable { navController.navigate(Screen.Settings.route) }
                        )
                    },
                    actions = {
                        if (useSearchIcon.value) {
                            IconButton(onClick = viewModel::showSearchDialog) {
                                SearchIcon()
                            }
                        }
                        if (showPaletteIcon.value) {
                            ThemeMenu(
                                selectedMode = themeMode.value,
                                onThemeSelected = viewModel::setThemeMode
                            )
                        }
                        if (showMoreMenu.value) {
                            MoreMenu(onOpenSettings = { navController.navigate(Screen.Settings.route) })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (useModernUi.value) {
                    AppNavigationBar(
                        showLabels = showBottomTabLabels.value,
                        currentRoute = currentRoute,
                        onCards = {
                            navController.navigate(Screen.TopScreen.route) {
                                popUpTo(Screen.TopScreen.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onStats = { navController.navigate(Screen.Stats.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
            },
        ) { innerPadding ->
            SuicaNFCReaderNavigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                viewModel
            )
        }
    }
}

@Composable
private fun SearchIcon() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.26f,
            center = Offset(size.width * 0.43f, size.height * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.08f)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.62f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = size.minDimension * 0.08f
        )
    }
}

@Composable
private fun ThemeMenu(
    selectedMode: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        PaletteIcon()
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        AppThemeMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = null
                        )
                        Text(mode.label)
                    }
                },
                onClick = {
                    onThemeSelected(mode)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun MoreMenu(
    onOpenSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Text(
            text = "︙",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("設定") },
            onClick = {
                onOpenSettings()
                expanded = false
            }
        )
    }
}

@Composable
private fun AppNavigationBar(
    showLabels: Boolean,
    currentRoute: String?,
    onCards: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        NavigationBarItem(
            selected = currentRoute == Screen.TopScreen.route,
            onClick = onCards,
            icon = { NavigationSymbol("card") },
            label = if (showLabels) {{ Text("カード") }} else null,
            alwaysShowLabel = showLabels
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Stats.route,
            onClick = onStats,
            icon = { NavigationSymbol("stats") },
            label = if (showLabels) {{ Text("統計") }} else null,
            alwaysShowLabel = showLabels
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Settings.route,
            onClick = onSettings,
            icon = { NavigationSymbol("settings") },
            label = if (showLabels) {{ Text("設定") }} else null,
            alwaysShowLabel = showLabels
        )
    }
}

@Composable
private fun NavigationSymbol(kind: String) {
    val color = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(24.dp)) {
        when (kind) {
            "card" -> {
                drawRoundRect(color = color, size = Size(size.width * .78f, size.height * .58f), topLeft = Offset(size.width * .11f, size.height * .21f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
                drawRect(color = surfaceColor, topLeft = Offset(size.width * .18f, size.height * .43f), size = Size(size.width * .64f, size.height * .08f))
            }
            "stats" -> {
                drawCircle(color = color, radius = size.minDimension * .36f, center = Offset(size.width / 2, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width / 2, size.height * .2f), 2.5f)
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width * .78f, size.height * .64f), 2.5f)
            }
            else -> {
                val center = Offset(size.width / 2, size.height / 2)
                val outerRadius = size.minDimension * .31f
                val innerRadius = size.minDimension * .12f
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45.0) - 90.0)
                    val start = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * outerRadius * .74f,
                        center.y + kotlin.math.sin(angle).toFloat() * outerRadius * .74f
                    )
                    val end = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * outerRadius,
                        center.y + kotlin.math.sin(angle).toFloat() * outerRadius
                    )
                    drawLine(color, start, end, 2.5f)
                }
                drawCircle(color, radius = outerRadius * .7f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                drawCircle(color, radius = innerRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
            }
        }
    }
}

@Composable
private fun PaletteIcon() {
    val base = MaterialTheme.colorScheme.onBackground
    val cutout = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(24.dp)) {
        drawOval(
            color = base,
            topLeft = Offset(size.width * 0.08f, size.height * 0.12f),
            size = Size(size.width * 0.78f, size.height * 0.72f)
        )
        drawCircle(
            color = cutout,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.64f, size.height * 0.62f)
        )
        drawCircle(primary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.33f, size.height * 0.35f))
        drawCircle(secondary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.52f, size.height * 0.31f))
        drawCircle(tertiary, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.39f, size.height * 0.55f))
    }
}

@Composable
private fun ApplySystemBars(themeMode: AppThemeMode) {
    val view = LocalView.current
    val lightBars = themeMode == AppThemeMode.WHITE

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = TRANSPARENT
        window.navigationBarColor = TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
