package com.mrdartsidetm.wasm.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrdartsidetm.wasm.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onImportClick: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val senders by viewModel.uniqueSenders.collectAsState()
    val mediaDir = viewModel.mediaDir

    // Show identity dialog if we have messages but no identity selected
    if (senders.isNotEmpty() && currentUser.isEmpty()) {
        IdentityDialog(senders) { selectedName ->
            viewModel.setIdentity(selectedName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wasm Viewer") },
                actions = {
                    TextButton(onClick = onImportClick) {
                        Text("Import")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                val isMe = message.sender == currentUser
                ChatBubble(message, isMe, mediaDir)
            }
        }
    }
}

@Composable
fun ChatBubble(message: MessageEntity, isMe: Boolean, mediaDir: File) {
    // Column used to align the entire bubble to Left or Right
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            // Material 3 Colors: Green-ish for "Me", SurfaceVariant for "Others"
            color = if (isMe) Color(0xFFD9FDD3) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMe) 12.dp else 0.dp, // Sharp corner on left for others
                bottomEnd = if (isMe) 0.dp else 12.dp    // Sharp corner on right for me
            ),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isMe) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Render media attachment if present
                message.mediaName?.let { filename ->
                    val file = File(mediaDir, filename)
                    val isImage = filename.endsWith(".jpg", ignoreCase = true) ||
                            filename.endsWith(".jpeg", ignoreCase = true) ||
                            filename.endsWith(".png", ignoreCase = true) ||
                            filename.endsWith(".webp", ignoreCase = true) ||
                            filename.endsWith(".gif", ignoreCase = true)

                    if (isImage) {
                        LocalImage(
                            file = file,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .heightIn(max = 240.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        NonImageAttachment(
                            filename = filename,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                // Conditionally display content text (hide if redundant "file attached" marker)
                val hasMedia = message.mediaName != null
                val contentToShow = if (hasMedia) {
                    val lower = message.content.lowercase()
                    if (lower.contains("file attached") || lower.startsWith("<attached:")) {
                        ""
                    } else {
                        message.content
                    }
                } else {
                    message.content
                }

                if (contentToShow.isNotEmpty()) {
                    Text(text = contentToShow, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = message.timestamp,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun LocalImage(file: File, modifier: Modifier = Modifier) {
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2 // Downsample to prevent OutOfMemory on large images
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                    bitmap?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    bitmapState.value?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Attached image",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color.LightGray.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Fallback or loading state
        Text(text = "Loading media...", style = MaterialTheme.typography.labelMedium, color = Color.DarkGray)
    }
}

@Composable
fun NonImageAttachment(filename: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📎", fontSize = 18.sp)
        Text(
            text = filename,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun IdentityDialog(senders: List<String>, onSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Which user are you?") },
        text = {
            Column {
                senders.forEach { name ->
                    TextButton(onClick = { onSelected(name) }) {
                        Text(name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {}
    )
}
