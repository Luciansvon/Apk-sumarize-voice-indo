package com.sumarize.voiceindo.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumarize.voiceindo.viewmodel.ChatMessage
import com.sumarize.voiceindo.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val messages = state.chatRoomMessages
    val isStreaming = state.isChatRoomStreaming
    val showTypingDots = isStreaming && (messages.isEmpty() || messages.last().role == "user")

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Hapus Percakapan?") },
            text = { Text("Semua pesan dalam sesi ini akan dihapus.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearChatRoom(); showClearDialog = false }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            ChatRoomTopBar(
                isOnlineMode = state.isOnlineMode,
                hasMessages = messages.isNotEmpty(),
                onBack = onNavigateBack,
                onClear = { showClearDialog = true }
            )
        },
        bottomBar = {
            ChatRoomInputBar(
                input = input,
                onInputChange = { input = it },
                isStreaming = isStreaming,
                onSend = {
                    val text = input.trim()
                    if (text.isNotBlank()) {
                        viewModel.sendChatRoomMessage(text)
                        input = ""
                    }
                }
            )
        }
    ) { padding ->
        if (messages.isEmpty() && !isStreaming) {
            ChatRoomEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onSuggestionClick = { viewModel.sendChatRoomMessage(it) }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(messages, key = { i, _ -> i }) { _, message ->
                    ChatRoomBubble(message = message)
                }
                if (showTypingDots) {
                    item(key = "typing") { TypingDots() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomTopBar(
    isOnlineMode: Boolean,
    hasMessages: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Gradient avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF26C6DA))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AI Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOnlineMode) Color(0xFF4CAF50) else Color(0xFF26C6DA))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isOnlineMode) "Online" else "Gemma 3 1B · Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
        },
        actions = {
            if (hasMessages) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus percakapan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun ChatRoomEmptyState(
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf(
        "Apa itu kecerdasan buatan?",
        "Bantu saya menulis email profesional",
        "Jelaskan cara kerja neural network",
        "Buat 5 pertanyaan interview kerja",
        "Apa perbedaan AI, ML, dan Deep Learning?",
        "Rekomendasikan cara belajar pemrograman"
    )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF26C6DA)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Halo! Aku siap membantu kamu.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tanya apa saja — aku akan menjawab dalam Bahasa Indonesia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Coba tanyakan ini:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { suggestion ->
                        OutlinedCard(
                            onClick = { onSuggestionClick(suggestion) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.weight(1.5f))
    }
}

@Composable
private fun ChatRoomBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isError = message.role == "error"

    when {
        isUser -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 4.dp
                            )
                        )
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF0288D1)))
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        isError -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    ),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        else -> {
            // AI (assistant or assistant_pending)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                // AI avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1565C0).copy(alpha = 0.8f),
                                    Color(0xFF26C6DA).copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    ),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")

    val dot0 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                1f at 200
                0.3f at 400
                0.3f at 1199
            },
            repeatMode = RepeatMode.Restart
        ), label = "d0"
    )
    val dot1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 180
                1f at 380
                0.3f at 580
                0.3f at 1199
            },
            repeatMode = RepeatMode.Restart
        ), label = "d1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 360
                1f at 560
                0.3f at 760
                0.3f at 1199
            },
            repeatMode = RepeatMode.Restart
        ), label = "d2"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1565C0).copy(alpha = 0.8f),
                            Color(0xFF26C6DA).copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(dot0, dot1, dot2).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Pesan...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                enabled = !isStreaming,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1565C0).copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            Spacer(Modifier.width(8.dp))

            val canSend = input.isNotBlank() && !isStreaming
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend)
                            Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF26C6DA)))
                        else
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                    )
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
