package com.sumarize.voiceindo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.data.preferences.AppPreferences
import com.sumarize.voiceindo.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    var apiKeyText by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(AppPreferences.AVAILABLE_MODELS.first().id) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingSwitchValue by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Load current prefs on first composition
    LaunchedEffect(Unit) {
        apiKeyText = viewModel.getApiKey()
        selectedModelId = viewModel.getSelectedModel()
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = {
                showPrivacyDialog = false
            },
            title = { Text("Peringatan Privasi") },
            text = {
                Text(
                    "Mode Online: teks transkripsi kamu akan dikirim ke server OpenRouter/pihak ketiga untuk diproses. " +
                    "Jika privasi penting, gunakan Mode Offline (model lokal berjalan 100% di HP kamu)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    viewModel.setOnlineMode(true)
                }) {
                    Text("Setuju & Aktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    // revert — don't change the mode
                }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Online mode switch card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Online (OpenRouter)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.isOnlineMode) "Aktif — ringkasan via API" else "Nonaktif — model lokal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isOnlineMode,
                        onCheckedChange = { newValue ->
                            if (newValue && !state.isOnlineMode) {
                                // First time turning on — show privacy disclaimer
                                pendingSwitchValue = newValue
                                showPrivacyDialog = true
                            } else {
                                viewModel.setOnlineMode(newValue)
                            }
                        }
                    )
                }
            }

            // Online mode settings (only when enabled)
            if (state.isOnlineMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Konfigurasi OpenRouter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // API Key field
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-or-v1-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Sembunyikan" else "Tampilkan"
                                    )
                                }
                            }
                        )

                        Button(
                            onClick = { viewModel.setApiKey(apiKeyText) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Simpan API Key")
                        }

                        HorizontalDivider()

                        // Model selection
                        Text(
                            text = "Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedLabel = AppPreferences.AVAILABLE_MODELS
                                .find { it.id == selectedModelId }?.label ?: selectedModelId

                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Pilih Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                AppPreferences.AVAILABLE_MODELS.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.label) },
                                        onClick = {
                                            selectedModelId = model.id
                                            viewModel.setModel(model.id)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Dapatkan API key gratis di openrouter.ai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Info card at the bottom
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Tentang Mode Pemrosesan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Mode Offline: semua pemrosesan terjadi sepenuhnya di HP kamu. Data tidak pernah meninggalkan perangkat — cocok jika privasi penting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Mode Online: transkripsi dikirim ke server OpenRouter/pihak ketiga untuk diproses oleh model AI yang dipilih. Kualitas ringkasan bisa lebih baik, namun data meninggalkan perangkat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
