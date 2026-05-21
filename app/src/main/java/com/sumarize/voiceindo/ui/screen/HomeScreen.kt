package com.sumarize.voiceindo.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.MainViewModel
import com.sumarize.voiceindo.viewmodel.ProcessingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecordAndProcess()
        else permissionDenied = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sumarize Voice", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "Riwayat")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            StatusBadge(step = state.step)

            Spacer(Modifier.height(48.dp))

            RecordButton(
                step = state.step,
                onClick = {
                    when (state.step) {
                        ProcessingStep.IDLE -> {
                            if (viewModel.hasAudioPermission()) viewModel.startRecordAndProcess()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        ProcessingStep.DONE, ProcessingStep.ERROR -> viewModel.reset()
                        else -> {}
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            if (permissionDenied) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Izin mikrofon diperlukan. Aktifkan di Pengaturan.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            AnimatedVisibility(visible = state.transcript.isNotBlank()) {
                ResultCard(
                    title = "Transkripsi",
                    content = state.transcript,
                    durationSeconds = state.durationSeconds
                )
            }

            val summaryText = state.streamingSummary.ifBlank { state.summary }
            AnimatedVisibility(visible = summaryText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ResultCard(
                    title = "Ringkasan",
                    content = summaryText,
                    isStreaming = state.step == ProcessingStep.SUMMARIZING
                )
            }

            state.errorMessage?.let { err ->
                Spacer(Modifier.height(12.dp))
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
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusBadge(step: ProcessingStep) {
    val (label, color) = when (step) {
        ProcessingStep.IDLE -> "Siap merekam" to MaterialTheme.colorScheme.outline
        ProcessingStep.RECORDING -> "Merekam..." to MaterialTheme.colorScheme.error
        ProcessingStep.TRANSCRIBING -> "Mentranskripsi..." to MaterialTheme.colorScheme.primary
        ProcessingStep.SUMMARIZING -> "Membuat ringkasan..." to MaterialTheme.colorScheme.tertiary
        ProcessingStep.DONE -> "Selesai" to MaterialTheme.colorScheme.secondary
        ProcessingStep.ERROR -> "Gagal" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RecordButton(step: ProcessingStep, onClick: () -> Unit) {
    val isRecording = step == ProcessingStep.RECORDING
    val isProcessing = step == ProcessingStep.TRANSCRIBING || step == ProcessingStep.SUMMARIZING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val buttonColor = when (step) {
        ProcessingStep.RECORDING -> MaterialTheme.colorScheme.error
        ProcessingStep.DONE -> MaterialTheme.colorScheme.secondary
        ProcessingStep.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }

    Box(contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulse)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        FloatingActionButton(
            onClick = { if (!isProcessing) onClick() },
            modifier = Modifier.size(80.dp),
            containerColor = buttonColor,
            shape = CircleShape
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Berhenti" else "Rekam",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = when (step) {
            ProcessingStep.IDLE -> "Ketuk untuk merekam"
            ProcessingStep.RECORDING -> "Berhenti otomatis saat hening"
            ProcessingStep.TRANSCRIBING -> "Memproses suara..."
            ProcessingStep.SUMMARIZING -> "Membuat ringkasan..."
            ProcessingStep.DONE -> "Ketuk untuk rekam baru"
            ProcessingStep.ERROR -> "Ketuk untuk coba lagi"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ResultCard(
    title: String,
    content: String,
    durationSeconds: Int = 0,
    isStreaming: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (durationSeconds > 0) {
                    Text(
                        text = "${durationSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isStreaming) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
