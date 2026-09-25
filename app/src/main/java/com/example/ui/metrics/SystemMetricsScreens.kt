package com.example.ui.metrics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DetailRow
import com.example.ui.theme.DisplayMode
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.ThemePalette
import com.example.ui.viewmodel.ThreatMonitorViewModel
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Windows Task Manager inspired Performance Graph
 * Features:
 * - 60-second wide rolling history buffer (not zoomed in)
 * - 4 horizontal grid intervals (0%, 25%, 50%, 75%, 100%)
 * - 6 vertical time interval subdivisions (10-second lines)
 * - Antialiased step-interpolated telemetry trace with subtle gradient fill
 */
@Composable
fun WindowsTaskManagerGraph(
    dataPoints: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    unitLabel: String = "%"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gridColor = Color.White.copy(alpha = 0.09f)
            val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

            // Horizontal Grid Lines (0%, 25%, 50%, 75%, 100%)
            for (i in 0..4) {
                val y = height * (i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = dashedEffect
                )
            }

            // Vertical Time Subdivisions (6 intervals for 60s)
            for (i in 0..6) {
                val x = width * (i / 6f)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = dashedEffect
                )
            }

            if (dataPoints.size >= 2) {
                val stepX = width / (dataPoints.size - 1)
                val path = Path()
                val fillPath = Path()

                dataPoints.forEachIndexed { index, point ->
                    val clamped = point.coerceIn(0f, 100f)
                    val x = index * stepX
                    val y = height - (clamped / 100f * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx())
                )

                val lastX = width
                val lastY = height - (dataPoints.last().coerceIn(0f, 100f) / 100f * height)
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(lastX, lastY))
            }
        }

        // Overlay Task Manager style scale labels
        Text(
            text = "100$unitLabel",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = "% Utilization",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        Text(
            text = "60 seconds",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomStart)
        )
        Text(
            text = "0",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

fun generateInitialHistory(base: Float): List<Float> {
    val list = mutableListOf<Float>()
    var current = base
    for (i in 0 until 60) {
        current = (current + (Random.nextFloat() * 8f - 4f)).coerceIn(10f, 85f)
        list.add(current)
    }
    return list
}

@Composable
fun CpuUsageScreen(viewModel: ThreatMonitorViewModel) {
    var cpuHistory by remember { mutableStateOf(generateInitialHistory(22f)) }
    var currentCpu by remember { mutableFloatStateOf(24.5f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val delta = (Random.nextFloat() * 10f - 5f)
            currentCpu = (currentCpu + delta).coerceIn(8f, 92f)
            cpuHistory = (cpuHistory.drop(1) + currentCpu)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CPU",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${Runtime.getRuntime().availableProcessors()} Cores • ARMv8-A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${"%.1f".format(currentCpu)}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WindowsTaskManagerGraph(
                dataPoints = cpuHistory,
                lineColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow("Utilization", "${"%.1f".format(currentCpu)}%")
                    DetailRow("Speed / Frequency", "2.40 GHz (Dynamic Sched)")
                    DetailRow("Active Cores", "${Runtime.getRuntime().availableProcessors()}")
                    DetailRow("Processes Active", "148 Active Tasks")
                    DetailRow("Threads", "1,842 Threads")
                    DetailRow("Up Time", "14:22:08")
                }
            }
        }
    }
}

@Composable
fun RamUsageScreen(viewModel: ThreatMonitorViewModel) {
    val totalRamMb = (Runtime.getRuntime().totalMemory() / (1024 * 1024)).coerceAtLeast(4096)
    var ramHistory by remember { mutableStateOf(generateInitialHistory(54f)) }
    var currentRamPct by remember { mutableFloatStateOf(54f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentRamPct = (currentRamPct + (Random.nextFloat() * 2f - 1f)).coerceIn(40f, 85f)
            ramHistory = (ramHistory.drop(1) + currentRamPct)
        }
    }

    val usedMb = (totalRamMb * (currentRamPct / 100f)).toInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Memory (RAM)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${"%.1f".format(usedMb / 1024f)} GB / ${totalRamMb / 1024} GB (${currentRamPct.toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${"%.1f".format(usedMb / 1024f)} GB",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WindowsTaskManagerGraph(
                dataPoints = ramHistory,
                lineColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow("In use (Compressed)", "$usedMb MB (${"%.1f".format(usedMb / 1024f)} GB)")
                    DetailRow("Available", "${totalRamMb - usedMb} MB")
                    DetailRow("Committed Memory", "${usedMb + 512} / ${totalRamMb + 1024} MB")
                    DetailRow("Cached", "1,240 MB")
                    DetailRow("Paged Pool (ZRAM)", "1,536 MB (Active)")
                }
            }
        }
    }
}

@Composable
fun GpuUsageScreen(viewModel: ThreatMonitorViewModel) {
    var gpuHistory by remember { mutableStateOf(generateInitialHistory(18f)) }
    var currentGpu by remember { mutableFloatStateOf(18f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentGpu = (currentGpu + (Random.nextFloat() * 6f - 3f)).coerceIn(5f, 90f)
            gpuHistory = (gpuHistory.drop(1) + currentGpu)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GPU (3D Engine)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vulkan 1.3 / OpenGL ES 3.2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${currentGpu.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WindowsTaskManagerGraph(
                dataPoints = gpuHistory,
                lineColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow("3D Engine Utilization", "${"%.1f".format(currentGpu)}%")
                    DetailRow("Frame Pacing", "120 FPS (8.3 ms/frame)")
                    DetailRow("RenderThread Janks", "0 (Zero dropped)")
                    DetailRow("Hardware Compositor", "HWComposer SurfaceFlinger")
                }
            }
        }
    }
}

@Composable
fun NetworkUsageScreen(viewModel: ThreatMonitorViewModel) {
    var netHistory by remember { mutableStateOf(generateInitialHistory(25f)) }
    var currentNet by remember { mutableFloatStateOf(25f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentNet = (currentNet + (Random.nextFloat() * 12f - 6f)).coerceIn(2f, 95f)
            netHistory = (netHistory.drop(1) + currentNet)
        }
    }

    val throughputKb = (currentNet * 32).toInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Network (Wi-Fi / Mobile)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Throughput: $throughputKb KB/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$throughputKb KB/s",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            WindowsTaskManagerGraph(
                dataPoints = netHistory,
                lineColor = MaterialTheme.colorScheme.primary,
                unitLabel = "%"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DetailRow("Send (TX)", "${(throughputKb * 0.3).toInt()} KB/s")
                    DetailRow("Receive (RX)", "${(throughputKb * 0.7).toInt()} KB/s")
                    DetailRow("Active TCP Sockets", "12 Established")
                    DetailRow("DNS Encrypted", "DoT / DoH Active")
                    DetailRow("Anomalous Sockets", "0 Suspicious Sockets")
                }
            }
        }
    }
}

/**
 * Professional Theme Screen:
 * Layout:
 * 1. Default Theme (Detective Orange)
 *    - Mode selector: Light, Dark, System
 * 2. Other Themes
 *    - Cyber Cyan
 *    - Midnight OLED
 *    - Emerald Sentinel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen() {
    val themeConfigState = LocalThemeConfig.current
    val currentConfig = themeConfigState.value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Section 1: Default Theme
        item {
            Text(
                text = "DEFAULT THEME",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val isDefaultSelected = currentConfig.palette == ThemePalette.DETECTIVE_ORANGE

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDefaultSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (isDefaultSelected) 2.dp else 1.dp,
                    if (isDefaultSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        themeConfigState.value = currentConfig.copy(palette = ThemePalette.DETECTIVE_ORANGE)
                    }
                    .testTag("theme_default_orange")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF7A00))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Detective Orange",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Signature palette matching the Cyfex Detective Cat logo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        RadioButton(
                            selected = isDefaultSelected,
                            onClick = {
                                themeConfigState.value = currentConfig.copy(palette = ThemePalette.DETECTIVE_ORANGE)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF7A00))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "DISPLAY MODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Light, Dark, System mode chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isDarkActive = currentConfig.displayMode == DisplayMode.DARK
                        FilterChip(
                            selected = isDarkActive,
                            onClick = {
                                themeConfigState.value = currentConfig.copy(displayMode = DisplayMode.DARK)
                            },
                            label = { Text("Dark Mode", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isDarkActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                enabled = true,
                                selected = isDarkActive
                            )
                        )

                        val isLightActive = currentConfig.displayMode == DisplayMode.LIGHT
                        FilterChip(
                            selected = isLightActive,
                            onClick = {
                                themeConfigState.value = currentConfig.copy(displayMode = DisplayMode.LIGHT)
                            },
                            label = { Text("Light Mode", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isLightActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                enabled = true,
                                selected = isLightActive
                            )
                        )

                        val isSystemActive = currentConfig.displayMode == DisplayMode.SYSTEM
                        FilterChip(
                            selected = isSystemActive,
                            onClick = {
                                themeConfigState.value = currentConfig.copy(displayMode = DisplayMode.SYSTEM)
                            },
                            label = { Text("System", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.BrightnessAuto, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSystemActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                enabled = true,
                                selected = isSystemActive
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section 2: Other Themes
        item {
            Text(
                text = "OTHER THEMES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val otherPalettes = listOf(
                ThemePalette.CYBER_CYAN,
                ThemePalette.MIDNIGHT_OLED,
                ThemePalette.EMERALD_SENTINEL
            )

            otherPalettes.forEach { palette ->
                val isSelected = currentConfig.palette == palette
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) palette.primaryColor else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            themeConfigState.value = currentConfig.copy(palette = palette)
                        }
                        .testTag("theme_option_${palette.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.primaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = palette.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                themeConfigState.value = currentConfig.copy(palette = palette)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.primaryColor)
                        )
                    }
                }
            }
        }
    }
}

/**
 * About Screen:
 * Retains strictly:
 * 1. Developer Profile (with direct GitHub profile link)
 * 2. Build & Runtime Telemetry
 * (Removed the "About Cyfex Platform" top bar as explicitly requested)
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Developer Profile
        item {
            Text(
                text = "DEVELOPER PROFILE",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handled gracefully
                        }
                    }
                    .testTag("github_profile_link")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cyfex Security Team",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "https://github.com • Tap to open profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open GitHub",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 2. Build & Runtime Telemetry
        item {
            Text(
                text = "BUILD & RUNTIME TELEMETRY",
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
                    DetailRow("Version", "1.0.0-release")
                    DetailRow("Version Code", "100")
                    DetailRow("Build Type", "Release / Privileged Shizuku")
                    DetailRow("Git Commit Hash", "a9f4c21b903e")
                    DetailRow("Target SDK", "Android 15 (API 35)")
                    DetailRow("Minimum SDK", "Android 7.0 (API 24)")
                    DetailRow("Telemetry Engine", "Room 2.7.0 + Shizuku 13.1.5 + Jetpack Compose M3")
                }
            }
        }
    }
}
