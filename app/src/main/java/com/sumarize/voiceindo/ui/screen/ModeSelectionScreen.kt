package com.sumarize.voiceindo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.SetupViewModel

@Composable
fun ModeSelectionScreen(
    viewModel: SetupViewModel,
    onModePicked: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                text = "Selamat Datang",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Pilih cara aplikasi memproses suara kamu. Bisa diubah nanti di Pengaturan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            ModeCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Mode Offline",
                subtitle = "~600 MB sekali download",
                bullets = listOf(
                    "Jalan 100% di HP, tanpa internet",
                    "Privasi penuh — data tidak keluar HP",
                    "Butuh download Whisper + Gemma"
                ),
                onClick = {
                    viewModel.chooseOfflineMode()
                    onModePicked()
                }
            )

            Spacer(Modifier.height(16.dp))

            ModeCard(
                icon = Icons.Default.Cloud,
                title = "Mode Online",
                subtitle = "0 MB download — butuh internet & API key",
                bullets = listOf(
                    "STT & ringkasan via OpenRouter (API key gratis tersedia)",
                    "Tidak perlu download model apapun",
                    "Akurasi lebih tinggi dengan Whisper API"
                ),
                highlighted = true,
                onClick = {
                    viewModel.chooseOnlineMode()
                    onModePicked()
                }
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Kamu masih bisa pindah mode kapan saja dari Pengaturan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    bullets: List<String>,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            bullets.forEach { bullet ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
