package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.VideoMediaManager
import com.example.domain.model.AnalysisStatus
import com.example.domain.model.CreatorCadenceSummary
import com.example.domain.model.VideoAnalysis
import com.example.ui.components.HookedBrandMark
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRelativeDate
import com.example.ui.components.formatTimestampMs
import com.example.ui.viewmodel.HomeBannerState
import com.example.ui.viewmodel.HomeViewModel
import java.io.File

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenResults: (String) -> Unit,
    onOpenCadence: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteVideo by remember { mutableStateOf<VideoAnalysis?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoPicked(uri) { startedVideoId ->
                onOpenResults(startedVideoId)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HomeTopBar(
                        cadence = uiState.cadenceSummary,
                        onOpenCadence = onOpenCadence
                    )
                }

                item {
                    StudioUploadHeroCard(
                        isImporting = uiState.bannerState is HomeBannerState.Importing,
                        onPickVideo = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                }

                item {
                    AnimatedVisibility(visible = uiState.bannerState is HomeBannerState.Error) {
                        val errorMsg = (uiState.bannerState as? HomeBannerState.Error)?.message.orEmpty()
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::dismissBannerError,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss error",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                uiState.cadenceSummary?.let { cadence ->
                    item {
                        CadenceQuickStrip(
                            cadence = cadence,
                            onClick = onOpenCadence
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Uploads",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.history.size} on device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (uiState.history.isEmpty()) {
                    item {
                        EmptyHistoryCard(
                            onPickVideo = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                    }
                } else {
                    items(uiState.history, key = { it.id }) { item ->
                        VideoHistoryCard(
                            analysis = item,
                            onOpen = { onOpenResults(item.id) },
                            onRetry = { viewModel.retryAnalysis(item.id) },
                            onDeleteRequest = { pendingDeleteVideo = item }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    pendingDeleteVideo?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteVideo = null },
            title = { Text("Delete upload?") },
            text = {
                Text(
                    "Remove \"${target.title}\" along with its highlights, exported clips, and generated thumbnails from this device?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVideo(target.id)
                        pendingDeleteVideo = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteVideo = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    cadence: CreatorCadenceSummary?,
    onOpenCadence: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HookedBrandMark()
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Device-local studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            onClick = onOpenCadence,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("cadence_nav_button")
        ) {
            Row(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = "Creator cadence",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (cadence != null && cadence.totalHookedUploads > 0) {
                        "${cadence.hookedCoveragePercent}% hooked"
                    } else {
                        stringResource(R.string.cadence_nav_label)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StudioUploadHeroCard(
    isImporting: Boolean,
    onPickVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF00B0D12),
                            Color(0xCC0B0D12),
                            Color(0x660B0D12)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "GEMINI 3.8 FLASH + NANO BANANA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "Find your strongest hooks before you post.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = "Get 3–8 scored moments, 5 titles, 5 AI-generated thumbnails, and ready-to-share clips.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD0D5E4)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPickVideo,
                    enabled = !isImporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("upload_video_button")
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importing...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_video_cta))
                    }
                }

                Text(
                    text = stringResource(R.string.upload_constraints),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB5BCD0)
                )
            }
        }
    }
}

@Composable
private fun CadenceQuickStrip(
    cadence: CreatorCadenceSummary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("cadence_quick_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CadenceMiniStat(
                label = "Every Upload?",
                value = "${cadence.hookedCoveragePercent}%",
                accent = MaterialTheme.colorScheme.tertiary
            )
            CadenceMiniStat(
                label = "Last 7 Days",
                value = "${cadence.uploadsLast7Days}/${cadence.targetUploadsPerWeek}",
                accent = MaterialTheme.colorScheme.secondary
            )
            CadenceMiniStat(
                label = "Clips Exported",
                value = cadence.totalClipsExported.toString(),
                accent = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CadenceMiniStat(
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@Composable
private fun VideoHistoryCard(
    analysis: VideoAnalysis,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("history_card_${analysis.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (analysis.coverFramePath != null && File(analysis.coverFramePath).exists()) {
                        AsyncImage(
                            model = File(analysis.coverFramePath),
                            contentDescription = analysis.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analysis.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTimestampMs(analysis.durationMs)} · ${VideoMediaManager.formatMegabytes(analysis.fileSizeBytes)} MB · ${formatRelativeDate(analysis.createdAtEpochMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_video_${analysis.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_video),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(
                    status = analysis.status,
                    hookCount = analysis.highlights.size
                )

                when (analysis.status) {
                    AnalysisStatus.ANALYZING -> {
                        Text(
                            text = analysis.statusDetail ?: "Processing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnalysisStatus.COMPLETED -> {
                        val topScore = analysis.highlights.maxOfOrNull { it.hookScore }
                        if (topScore != null) {
                            Text(
                                text = "Top Hook $topScore/100 · ${analysis.thumbnailIdeas.count { it.generatedImagePath != null }}/5 images",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AnalysisStatus.FAILED -> {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_analysis_${analysis.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.retry_analysis))
                        }
                    }
                }
            }

            if (analysis.status == AnalysisStatus.FAILED && !analysis.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = analysis.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard(onPickVideo: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MovieCreation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = stringResource(R.string.no_uploads_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.no_uploads_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onPickVideo,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Choose from Gallery")
            }
        }
    }
}
