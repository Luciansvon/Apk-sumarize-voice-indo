package com.sumarize.voiceindo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    summaryId: Long,
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val selected by viewModel.selected.collectAsState()

    LaunchedEffect(summaryId) { viewModel.loadById(summaryId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.title ?: "Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        val entity = selected
        if (entity == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id"))
                                    .format(Date(entity.timestamp)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                formatDuration(entity.durationSeconds),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                SectionCard(title = "Ringkasan", content = entity.summary)
                SectionCard(title = "Transkripsi Lengkap", content = entity.transcript)

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
