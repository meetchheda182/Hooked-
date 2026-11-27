package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CreatorCadenceSummary
import com.example.domain.model.UploadActivityItem
import com.example.domain.model.UploadEventType
import com.example.domain.model.WeeklyUploadBucket
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.CadenceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceScreen(
    viewModel: CadenceViewModel,
    onBack: () -> Unit
) {
    val summary by viewModel.cadenceState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cadence_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Creator Cadence",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Device upload frequency & Hooked coverage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val data = summary
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        CoverageHeroCard(summary = data)
                    }

                    item {
                        KpiGrid(summary = data)
                    }

                    item {
                        WeeklyCadenceChartCard(
                            buckets = data.weeklyBuckets,
                            targetPerWeek = data.targetUploadsPerWeek
                        )
                    }

                    item {
                        TargetScheduleCard(
                            currentTarget = data.targetUploadsPerWeek,
                            onSelectTarget = viewModel::setTargetUploadsPerWeek
                        )
                    }

                    item {
                        ExternalUploadCheckInCard(
                            onLogExternal = {
                                viewModel.logExternalUpload()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Logged non-Hooked upload to cadence tracker")
                                }
                            }
                        )
                    }

                    item {
                        Text(
                            text = "Studio Activity Log",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (data.recentActivities.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Text(
                                    text = "Upload your first video to start tracking how consistently you use Hooked.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(data.recentActivities, key = { it.id }) { event ->
                            ActivityEventRow(event = event)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CoverageHeroCard(summary: CreatorCadenceSummary) {
    val pct = summary.hookedCoveragePercent
    val ringColor = when {
        pct >= 80 -> MaterialTheme.colorScheme.tertiary
        pct >= 50 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(86.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (pct / 100f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DO THEY USE IT EVERY UPLOAD?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.verdictTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${summary.totalHookedUploads} Hooked uploads vs. ${summary.totalExternalUploads} logged outside Hooked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KpiGrid(summary: CreatorCadenceSummary) {
    val avgIntervalText = summary.avgDaysBetweenUploads?.let {
        String.format(Locale.US, "%.1fd", it)
    } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                label = "Last 7 / 30 Days",
                value = "${summary.uploadsLast7Days} / ${summary.uploadsLast30Days}",
                sub = "uploads analyzed",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Avg Upload Pace",
                value = avgIntervalText,
                sub = "between uploads",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                label = "Weekly Streak",
                value = "${summary.currentWeeklyStreak} wk",
                sub = "consecutive active",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Packaging Used",
                value = "${summary.packagingAdoptionPercent}%",
                sub = "${summary.totalClipsExported} clips · ${summary.totalThumbnailsGenerated} imgs",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(16.dp)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyCadenceChartCard(
    buckets: List<WeeklyUploadBucket>,
    targetPerWeek: Int
) {
    val hookedColor = MaterialTheme.colorScheme.primary
    val externalColor = MaterialTheme.colorScheme.outline
    val targetLineColor = MaterialTheme.colorScheme.secondary

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "6-Week Upload Cadence",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = hookedColor, label = "Hooked")
                    LegendDot(color = externalColor, label = "Other")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = maxOf(
                targetPerWeek,
                buckets.maxOfOrNull { it.hookedUploads + it.externalUploads } ?: 1,
                2
            ).toFloat()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val targetY = size.height * (1f - (targetPerWeek / maxVal))
                drawLine(
                    color = targetLineColor.copy(alpha = 0.7f),
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                val count = buckets.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.44f).coerceAtMost(36.dp.toPx())

                buckets.forEachIndexed { index, bucket ->
                    val centerX = slotWidth * index + slotWidth / 2f
                    val left = centerX - barWidth / 2f

                    val hookedH = (bucket.hookedUploads / maxVal) * size.height
                    val extH = (bucket.externalUploads / maxVal) * size.height

                    if (hookedH > 0f) {
                        drawRoundRect(
                            color = hookedColor,
                            topLeft = Offset(left, size.height - hookedH),
                            size = Size(barWidth, hookedH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                    if (extH > 0f) {
                        drawRoundRect(
                            color = externalColor,
                            topLeft = Offset(left, size.height - hookedH - extH - 2.dp.toPx()),
                            size = Size(barWidth, extH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                buckets.forEach { bucket ->
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TargetScheduleCard(
    currentTarget: Int,
    onSelectTarget: (Int) -> Unit
) {
    val options = listOf(1, 2, 3, 5, 7)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Publishing Goal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Set how often you aim to publish so Hooked measures your consistency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { target ->
                    FilterChip(
                        selected = currentTarget == target,
                        onClick = { onSelectTarget(target) },
                        label = { Text("${target}x/wk") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("target_schedule_$target")
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalUploadCheckInCard(onLogExternal: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Uploaded without Hooked?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Log skipped uploads to keep your \"every upload\" coverage score honest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = onLogExternal,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("log_external_upload_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log")
            }
        }
    }
}

@Composable
private fun ActivityEventRow(event: UploadActivityItem) {
    val icon = when (event.eventType) {
        UploadEventType.VIDEO_ANALYZED -> Icons.Rounded.MovieCreation
        UploadEventType.CLIP_EXPORTED -> Icons.Rounded.ContentCut
        UploadEventType.THUMBNAIL_GENERATED -> Icons.Rounded.AutoAwesome
        UploadEventType.TITLE_COPIED -> Icons.Rounded.ContentCopy
        UploadEventType.EXTERNAL_UPLOAD_LOGGED -> Icons.Rounded.Public
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = event.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatRelativeDate(event.timestampEpochMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
