package com.sumarize.voiceindo.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.ChatMessage
import com.sumarize.voiceindo.viewmodel.MainViewModel
import com.sumarize.voiceindo.viewmodel.ProcessingStep
import com.sumarize.voiceindo.viewmodel.RecordingTemplate
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var permissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Pengaturan")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "Riwayat")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            StatusBadge(step = state.step)

            if (state.step == ProcessingStep.IDLE) {
                Spacer(Modifier.height(16.dp))
                TemplateSelector(
                    selected = state.selectedTemplate,
                    onSelect = { viewModel.setTemplate(it) }
                )
            }

            Spacer(Modifier.height(32.dp))

            RecordButton(
                step = state.step,
                recordingSeconds = state.recordingSeconds,
                currentAmplitude = state.currentAmplitude,
                onClick = {
                    when (state.step) {
                        ProcessingStep.IDLE -> {
                            if (viewModel.hasAudioPermission()) viewModel.startRecordAndProcess()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        ProcessingStep.RECORDING -> viewModel.stopRecording()
                        ProcessingStep.DONE, ProcessingStep.ERROR -> viewModel.reset()
                        else -> {}
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = state.step == ProcessingStep.RECORDING && state.liveTranscript.isNotBlank()
            ) {
                LiveTranscriptCard(text = state.liveTranscript)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))

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

            if (state.step == ProcessingStep.DONE && state.summary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.summary)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan ringkasan"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bagikan")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                                    ?: context.filesDir
                                val file = File(dir, "sumarize_${System.currentTimeMillis()}.txt")
                                file.writeText("TRANSKRIPSI:\n${state.transcript}\n\nRINGKASAN:\n${state.summary}")
                                scope.launch { snackbarHostState.showSnackbar("File disimpan di Documents/") }
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Gagal menyimpan file: ${e.message}") }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ekspor TXT")
                    }
                }

                Spacer(Modifier.height(16.dp))
                ChatSection(
                    messages = state.chatMessages,
                    isStreaming = state.isChatStreaming,
                    onSend = { viewModel.sendChatMessage(it) }
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
private fun TemplateSelector(
    selected: RecordingTemplate,
    onSelect: (RecordingTemplate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Jenis Rekaman",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingTemplate.entries.forEach { template ->
                FilterChip(
                    selected = template == selected,
                    onClick = { onSelect(template) },
                    label = { Text(template.displayName) }
                )
            }
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

private val WAVEFORM_MULTIPLIERS = listOf(0.4f, 0.9f, 0.6f, 1.0f, 0.7f, 1.0f, 0.5f, 0.8f, 0.3f)

@Composable
private fun WaveformBars(amplitude: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WAVEFORM_MULTIPLIERS.forEach { multiplier ->
            val targetHeight: Dp = if (amplitude > 0f) {
                (amplitude * 300f * multiplier).dp.coerceIn(4.dp, 40.dp)
            } else {
                4.dp
            }
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "wavebar"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun RecordButton(
    step: ProcessingStep,
    recordingSeconds: Int = 0,
    currentAmplitude: Float = 0f,
    onClick: () -> Unit
) {
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

    if (isRecording) {
        val minutes = recordingSeconds / 60
        val seconds = recordingSeconds % 60
        val timerText = "%02d:%02d".format(minutes, seconds)
        Text(
            text = timerText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        WaveformBars(amplitude = currentAmplitude)
    } else {
        Text(
            text = when (step) {
                ProcessingStep.IDLE -> "Ketuk untuk merekam"
                ProcessingStep.TRANSCRIBING -> "Memproses suara..."
                ProcessingStep.SUMMARIZING -> "Membuat ringkasan..."
                ProcessingStep.DONE -> "Ketuk untuk rekam baru"
                ProcessingStep.ERROR -> "Ketuk untuk coba lagi"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LiveTranscriptCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Mendengarkan...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
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

@Composable
private fun ChatSection(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tanya / Revisi Ringkasan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (messages.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Contoh: \"Buat lebih singkat\", \"Tambah poin tentang X\", atau \"Apa keputusan akhirnya?\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    messages.forEach { msg ->
                        ChatBubble(message = msg)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ketik pertanyaan atau revisi...") },
                    modifier = Modifier.weight(1f),
                    enabled = !isStreaming,
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSend(input.trim())
                            input = ""
                        }
                    },
                    enabled = !isStreaming && input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim")
                }
            }

            if (isStreaming) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mengetik...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bgColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surface
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            color = bgColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}
