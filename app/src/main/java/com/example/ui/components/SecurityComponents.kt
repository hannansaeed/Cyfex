package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shizuku.ShizukuStatus
import com.example.ui.theme.*

@Composable
fun RiskBadge(
    riskLevel: String,
    score: Int? = null,
    modifier: Modifier = Modifier
) {
    val (bg, textColor) = when (riskLevel.uppercase()) {
        "CRITICAL" -> Pair(RiskCritical.copy(alpha = 0.2f), RiskCritical)
        "HIGH" -> Pair(RiskHigh.copy(alpha = 0.2f), RiskHigh)
        "MEDIUM" -> Pair(RiskMedium.copy(alpha = 0.2f), RiskMedium)
        "LOW" -> Pair(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), MaterialTheme.colorScheme.primary)
        else -> Pair(RiskLow.copy(alpha = 0.2f), RiskLow)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(textColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (score != null) "$riskLevel ($score)" else riskLevel,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ShizukuStatusBanner(
    status: ShizukuStatus,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bannerColor, icon, actionText) = when (status) {
        ShizukuStatus.READY -> Triple(
            RiskLow,
            Icons.Default.VerifiedUser,
            "Active"
        )
        ShizukuStatus.PERMISSION_REQUIRED -> Triple(
            RiskMedium,
            Icons.Default.Security,
            "Grant Permission"
        )
        ShizukuStatus.NOT_RUNNING -> Triple(
            RiskHigh,
            Icons.Default.Warning,
            "Start Shizuku"
        )
        ShizukuStatus.NOT_INSTALLED -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Download,
            "Install Shizuku"
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("shizuku_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(bannerColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Shizuku Status",
                    tint = bannerColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Shizuku ADB Bridge",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(bannerColor, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            if (status != ShizukuStatus.READY) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (status == ShizukuStatus.PERMISSION_REQUIRED) {
                            onRequestPermission()
                        } else {
                            onOpenShizuku()
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = bannerColor.copy(alpha = 0.2f),
                        contentColor = bannerColor
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("shizuku_action_button")
                ) {
                    Text(
                        text = actionText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Data model for the 8 Real Android Security Controls
 */
data class SecurityControl(
    val id: String,
    val title: String,
    val score: Int,
    val maxScore: Int,
    val isLeft: Boolean
)

/**
 * Segmented Donut Assessment Dial matching the reference design:
 * - 8 Segmented Donut Arcs around center
 * - Solid Green for 100% passed
 * - Solid Red + Hatched remainder for partial scores
 * - Red Hatched for 0% failed
 * - Center Circular Badge with bold calculated Score
 * - Pointer rule lines connected to labels
 */
@Composable
fun SegmentedSecurityControlsDial(
    controls: List<SecurityControl>,
    overallScore: Int,
    modifier: Modifier = Modifier
) {
    val passedColor = Color(0xFF00E676)
    val criticalColor = Color(0xFFFF1744)
    val hatchedDarkBg = Color(0xFF182232)
    val hatchedStripeColor = Color(0xFF384A65)
    val centerBg = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Controls (5 Controls)
            val leftControls = controls.filter { it.isLeft }
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                leftControls.forEach { control ->
                    SecurityControlItemView(control = control, isAlignRight = false)
                }
            }

            // Center Circular Donut Chart
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val diameter = size.minDimension
                    val strokeWidth = 26.dp.toPx()
                    val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
                    val arcOffset = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    val segmentAngle = 360f / 8f // 45 degrees
                    val gap = 3.5f
                    val sweep = segmentAngle - gap

                    val startAngles = listOf(
                        -68f,  // Right 1: Unwanted Apps
                        -23f,  // Right 2: Network & Sockets
                        22f,   // Right 3: Anti-Malware / Process Telemetry
                        67f,   // Left 5: Dangerous Permissions
                        112f,  // Left 4: Privileged & SELinux
                        157f,  // Left 3: Device Encryption
                        202f,  // Left 2: OS & Patch Level
                        247f   // Left 1: Vulnerable Apps
                    )

                    controls.forEachIndexed { index, control ->
                        if (index < startAngles.size) {
                            val startAngle = startAngles[index]
                            val ratio = (control.score.toFloat() / control.maxScore.toFloat()).coerceIn(0f, 1f)

                            if (ratio >= 1.0f) {
                                // Safe / Clean: Solid Bright Green
                                drawArc(
                                    color = passedColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                            } else if (ratio == 0f) {
                                // 0% Failed / Critical: Red Diagonal Hatched
                                drawArc(
                                    color = criticalColor.copy(alpha = 0.25f),
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                drawArc(
                                    color = criticalColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Butt,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                )
                            } else {
                                // Partial (e.g. 10 out of 25): Solid Red filled for score, striped dark for missing gap
                                val filledSweep = sweep * ratio
                                drawArc(
                                    color = criticalColor,
                                    startAngle = startAngle,
                                    sweepAngle = filledSweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                drawArc(
                                    color = hatchedDarkBg,
                                    startAngle = startAngle + filledSweep,
                                    sweepAngle = sweep - filledSweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                drawArc(
                                    color = hatchedStripeColor,
                                    startAngle = startAngle + filledSweep,
                                    sweepAngle = sweep - filledSweep,
                                    useCenter = false,
                                    topLeft = arcOffset,
                                    size = arcSize,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Butt,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                    )
                                )
                            }
                        }
                    }

                    // Center Solid Circle
                    val centerRadius = (diameter - strokeWidth * 2) / 2f - 2f
                    drawCircle(
                        color = centerBg,
                        radius = centerRadius,
                        center = Offset(diameter / 2f, diameter / 2f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = centerRadius,
                        center = Offset(diameter / 2f, diameter / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Center Score Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$overallScore",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right Controls (3 Controls)
            val rightControls = controls.filter { !it.isLeft }
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                rightControls.forEach { control ->
                    SecurityControlItemView(control = control, isAlignRight = true)
                }
            }
        }
    }
}

@Composable
fun SecurityControlItemView(
    control: SecurityControl,
    isAlignRight: Boolean
) {
    val isPerfect = control.score == control.maxScore
    val isZero = control.score == 0
    val scoreTextColor = when {
        isPerfect -> Color(0xFF00E676)
        isZero -> Color(0xFFFF1744)
        else -> Color(0xFFFF9100)
    }

    Column(
        horizontalAlignment = if (isAlignRight) Alignment.End else Alignment.Start
    ) {
        Text(
            text = control.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            textAlign = if (isAlignRight) TextAlign.End else TextAlign.Start,
            lineHeight = 13.sp
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "${control.score} out of ${control.maxScore}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = scoreTextColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = if (isAlignRight) TextAlign.End else TextAlign.Start
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag("metric_stat_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(max = 135.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
