package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shizuku.ShizukuStatus
import com.example.ui.components.DetailRow
import com.example.ui.components.ShizukuStatusBanner
import com.example.ui.theme.*
import com.example.ui.viewmodel.ThreatMonitorViewModel

@Composable
fun SettingsScreen(
    viewModel: ThreatMonitorViewModel
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val isServiceActive by viewModel.isServiceActive.collectAsState()

    var showGuideDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
    ) {
        // Shizuku Setup Section
        item {
            Text(
                text = "SHIZUKU PRIVILEGED BRIDGE CONFIGURATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ShizukuStatusBanner(
                status = shizukuStatus,
                onRequestPermission = { viewModel.requestShizukuPermission() },
                onOpenShizuku = { viewModel.openShizukuApp() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { showGuideDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_shizuku_guide_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Wireless Debugging Setup Guide", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Real-Time Foreground Protection Service
        item {
            Text(
                text = "REAL-TIME PERSISTENT MONITORING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Foreground Monitoring Service",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Monitors process anomalies and background network sockets continuously with sticky notification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isServiceActive,
                        onCheckedChange = { viewModel.toggleMonitoringService() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("monitoring_service_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Controlled Threat Test Suite
        item {
            Text(
                text = "CONTROLLED THREAT SIMULATION SUITE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Test the complete pipeline (Collectors -> Normalizer -> Rules -> ML -> Scoring) using harmless simulated behavioral vectors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            SimulationButton(
                title = "Simulate Cryptominer Vector",
                subtitle = "Triggers RULE-001 (Persistence) & RULE-002 (88.5% CPU Spike)",
                color = RiskCritical,
                onClick = { viewModel.injectTestScenario("CRYPTOMINER") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            SimulationButton(
                title = "Simulate Spyware / Exfiltration Vector",
                subtitle = "Triggers RULE-004 (C2 Port 4444) & dangerous location/audio access",
                color = RiskHigh,
                onClick = { viewModel.injectTestScenario("SPYWARE") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            SimulationButton(
                title = "Simulate Dynamic DEX Injection Vector",
                subtitle = "Triggers RULE-005 (DexClassLoader payload unpacked in memory)",
                color = MaterialTheme.colorScheme.primary,
                onClick = { viewModel.injectTestScenario("DEX_INJECTION") }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Device Telemetry Specs
        item {
            Text(
                text = "DEVICE TELEMETRY SPECS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow("Device Model", "${deviceInfo.manufacturer} ${deviceInfo.model}")
                    DetailRow("Platform", deviceInfo.androidVersion)
                    DetailRow("Kernel", deviceInfo.kernelVersion)
                    DetailRow("Security Patch", deviceInfo.securityPatchLevel)
                    DetailRow("Wireless Debugging", if (deviceInfo.isWirelessDebuggingSupported) "Supported (Android 11+)" else "Requires USB ADB")
                    DetailRow("Shizuku Status", shizukuStatus.title)
                }
            }
        }
    }

    // Wireless Debugging Guide Dialog
    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text("Shizuku Setup: Wireless Debugging", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "On Android 11+, Shizuku can start completely on-device without a PC at any point:\n\n" +
                                "1. Install Shizuku from Play Store or GitHub.\n" +
                                "2. Enable 'Developer Options' in Android Settings.\n" +
                                "3. Connect to any Wi-Fi network.\n" +
                                "4. In Developer Options, enable 'Wireless Debugging'.\n" +
                                "5. Open Shizuku -> tap 'Pairing' -> enter pairing code.\n" +
                                "6. Tap 'Start' in Shizuku.\n" +
                                "7. Return to Cyfex and tap 'Grant Permission'.\n\n" +
                                "Note: On Android 10 and below, run 'adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh' once from a PC."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuideDialog = false
                        viewModel.openShizukuApp()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Open Shizuku App", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun SimulationButton(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("simulate_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Inject", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
