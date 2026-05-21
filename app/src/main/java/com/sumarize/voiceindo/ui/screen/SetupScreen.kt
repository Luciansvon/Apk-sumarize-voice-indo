package com.sumarize.voiceindo.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onSetupComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isReady) {
        if (state.isReady) onSetupComplete()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (state.onlineMode) Icons.Default.Cloud else Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (state.onlineMode) "Mode Online Siap" else "Persiapan Model AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (state.onlineMode)
                    "STT & ringkasan diproses via OpenRouter API. Tidak ada download diperlukan."
                else
                    "Mode Offline: download sekali ~600MB, lalu jalan 100% di HP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            if (!state.onlineMode) {
                ModelDownloadItem(
                    label = "Model STT (Whisper base int8)",
                    size = "~75 MB",
                    isReady = state.whisperReady,
                    isDownloading = state.isDownloadingWhisper,
                    progress = state.whisperProgress
                )

                Spacer(Modifier.height(16.dp))

                ModelDownloadItem(
                    label = "Model LLM (Gemma 3 1B int4)",
                    size = "~529 MB",
                    isReady = state.gemmaReady,
                    isDownloading = state.isDownloadingGemma,
                    progress = state.gemmaProgress
                )

                Spacer(Modifier.height(32.dp))
            }

            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!state.isDownloading && !state.isReady) {
                Button(
                    onClick = { viewModel.downloadRequired() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Mulai Download", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (state.isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.isDownloadingWhisper) "Mengunduh model STT..." else "Mengunduh model LLM...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelDownloadItem(
    label: String,
    size: String,
    isReady: Boolean,
    isDownloading: Boolean,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isDownloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (isReady) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Siap",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
