package com.example.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.applications.ApplicationsScreen
import com.example.ui.behavior.BehaviorScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.findings.FindingsScreen
import com.example.ui.metrics.*
import com.example.ui.monitoring.ForegroundMonitoringScreen
import com.example.ui.processes.ProcessesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.*
import com.example.ui.usage.AppUsageScreen
import com.example.ui.viewmodel.ThreatMonitorViewModel
import kotlinx.coroutines.launch

enum class MainScreen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    APPLICATIONS("Apps", Icons.Default.Apps),
    PROCESSES("Processes", Icons.Default.Memory),
    FINDINGS("Findings", Icons.Default.BugReport),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class DrawerScreen(val title: String, val icon: ImageVector) {
    FOREGROUND_MONITORING("Foreground Monitor", Icons.Default.Security),
    USAGE("App Usage", Icons.Default.DataUsage),
    TIMELINE("Timeline", Icons.Default.Timeline),
    CPU("CPU", Icons.Default.Memory),
    RAM("RAM", Icons.Default.Storage),
    GPU("GPU", Icons.Default.VideogameAsset),
    NETWORK("Network", Icons.Default.Wifi),
    THEMES("Themes", Icons.Default.Palette),
    ABOUT("About", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatMonitorNavHost(
    viewModel: ThreatMonitorViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentMainScreen by remember { mutableStateOf(MainScreen.DASHBOARD) }
    var currentDrawerScreen by remember { mutableStateOf<DrawerScreen?>(null) }

    // Right-side Drawer Navigation:
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerTonalElevation = 0.dp,
                        modifier = Modifier
                            .width(300.dp)
                            .testTag("right_navigation_drawer")
                    ) {
                        // Professional Drawer Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_cyfex_logo),
                                    contentDescription = "Cyfex Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Cyfex",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "v${com.example.BuildConfig.VERSION_NAME} • Threat Telemetry",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Drawer Navigation Items
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DrawerScreen.values().forEach { screen ->
                                val isSelected = currentDrawerScreen == screen
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        currentDrawerScreen = screen
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        unselectedContainerColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("drawer_item_${screen.name.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Authorized Shizuku ADB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(RiskLow, CircleShape)
                            )
                        }
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_cyfex_logo),
                                        contentDescription = "Cyfex Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Cyfex",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.triggerScan() },
                                    modifier = Modifier.testTag("topbar_refresh_scan_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Scan & Refresh",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    },
                                    modifier = Modifier.testTag("topbar_menu_three_bars_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = "Open Sidebar",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            MainScreen.values().forEach { screen ->
                                val isSelected = currentDrawerScreen == null && currentMainScreen == screen
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        currentDrawerScreen = null
                                        currentMainScreen = screen
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (currentDrawerScreen != null) {
                            when (currentDrawerScreen) {
                                DrawerScreen.FOREGROUND_MONITORING -> ForegroundMonitoringScreen(viewModel = viewModel)
                                DrawerScreen.USAGE -> AppUsageScreen(viewModel = viewModel)
                                DrawerScreen.TIMELINE -> BehaviorScreen(viewModel = viewModel)
                                DrawerScreen.CPU -> CpuUsageScreen(viewModel = viewModel)
                                DrawerScreen.RAM -> RamUsageScreen(viewModel = viewModel)
                                DrawerScreen.GPU -> GpuUsageScreen(viewModel = viewModel)
                                DrawerScreen.NETWORK -> NetworkUsageScreen(viewModel = viewModel)
                                DrawerScreen.THEMES -> ThemesScreen()
                                DrawerScreen.ABOUT -> AboutScreen()
                                null -> {}
                            }
                        } else {
                            when (currentMainScreen) {
                                MainScreen.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToApps = { currentMainScreen = MainScreen.APPLICATIONS },
                                    onNavigateToProcesses = { currentMainScreen = MainScreen.PROCESSES },
                                    onNavigateToFindings = { currentMainScreen = MainScreen.FINDINGS },
                                    onNavigateToSettings = { currentMainScreen = MainScreen.SETTINGS }
                                )
                                MainScreen.APPLICATIONS -> ApplicationsScreen(viewModel = viewModel)
                                MainScreen.PROCESSES -> ProcessesScreen(viewModel = viewModel)
                                MainScreen.FINDINGS -> FindingsScreen(viewModel = viewModel)
                                MainScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
