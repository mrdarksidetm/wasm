package com.mrdartsidetm.wasm.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrdartsidetm.wasm.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Material 3 Expressive Container Color backward-compatibility extensions
private val ColorScheme.surfaceContainerLowest: Color
    get() = surface.copy(alpha = 0.95f)

private val ColorScheme.surfaceContainerLow: Color
    get() = surfaceVariant.copy(alpha = 0.5f)

private val ColorScheme.surfaceContainer: Color
    get() = surfaceVariant.copy(alpha = 0.7f)

private val ColorScheme.surfaceContainerHigh: Color
    get() = surfaceVariant.copy(alpha = 0.85f)

private val ColorScheme.surfaceContainerHighest: Color
    get() = surfaceVariant

/**
 * Memory-safe LRU Cache for decoded WhatsApp attachment bitmaps.
 * Caches up to 1/8th of available runtime memory to prevent OutOfMemory crashes while maintaining 60-120 FPS.
 */
object BitmapMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    private val lru = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int {
            return (bitmap.width * bitmap.height * 4) / 1024
        }
    }

    fun get(key: String): ImageBitmap? = lru.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        lru.put(key, bitmap)
    }
}

/**
 * Two-pass memory-safe bitmap decoder that calculates inSampleSize power-of-two.
 */
fun decodeSampledBitmap(file: File, reqWidth: Int = 600, reqHeight: Int = 600): ImageBitmap? {
    if (!file.exists() || file.length() == 0L) return null
    return try {
        // Pass 1: query dimensions without allocating heap memory
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Pass 2: calculate inSampleSize
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        bitmap?.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

/**
 * Formats a file size in bytes to human-readable string (KB, MB).
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return if (bytes < 1024 * 1024) {
        "${bytes / 1024} KB"
    } else {
        String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}

/**
 * Extracts the date portion (e.g. "12/05/23" or "12/05/2023") from a timestamp.
 */
fun extractDate(timestamp: String): String {
    val commaIndex = timestamp.indexOf(',')
    return if (commaIndex != -1) {
        timestamp.substring(0, commaIndex).trim()
    } else {
        timestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onImportClick: () -> Unit) {
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val allMessages by viewModel.messages.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val senders by viewModel.uniqueSenders.collectAsStateWithLifecycle()
    val importState by viewModel.importUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val mediaDir = viewModel.mediaDir

    var showIdentityDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var fullScreenImageFile by remember { mutableStateOf<File?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-prompt identity selection if messages exist but identity is not configured
    LaunchedEffect(senders, currentUser) {
        if (senders.isNotEmpty() && currentUser.isEmpty()) {
            showIdentityDialog = true
        }
    }

    if (showIdentityDialog && senders.isNotEmpty()) {
        IdentitySelectionDialog(
            senders = senders,
            currentSelected = currentUser,
            onSelected = { selectedName ->
                viewModel.setIdentity(selectedName)
                showIdentityDialog = false
            },
            onDismiss = { showIdentityDialog = false }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear Chat Data?") },
            text = { Text("This will remove all imported messages and extracted media from your local database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen image viewer dialog
    fullScreenImageFile?.let { file ->
        FullScreenImageDialog(file = file, onDismiss = { fullScreenImageFile = null })
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Wasm Chat Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (allMessages.isNotEmpty()) {
                                val subtitle = if (searchQuery.isNotBlank()) {
                                    "${messages.size} found of ${allMessages.size}"
                                } else {
                                    "${allMessages.size} messages" + if (currentUser.isNotBlank()) " • Me: $currentUser" else ""
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (allMessages.isNotEmpty()) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search messages")
                            }
                            if (senders.isNotEmpty()) {
                                IconButton(onClick = { showIdentityDialog = true }) {
                                    Icon(Icons.Default.Person, contentDescription = "Switch User Identity")
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = onImportClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Import", style = MaterialTheme.typography.labelMedium)
                        }
                        if (allMessages.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Switch Identity") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showIdentityDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Chat") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showClearChatDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (allMessages.isEmpty() && importState !is ImportUiState.Loading) {
                // Material 3 Expressive Empty State
                ExpressiveEmptyState(
                    onImportClick = onImportClick,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Chat Message Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var previousDate = ""
                    items(messages, key = { it.id }) { message ->
                        val currentDate = extractDate(message.timestamp)
                        if (currentDate != previousDate && currentDate.isNotEmpty()) {
                            DateSeparatorHeader(date = currentDate)
                            previousDate = currentDate
                        }

                        if (message.isSystemMessage) {
                            SystemMessageChip(content = message.content)
                        } else {
                            val isMe = message.sender == currentUser
                            ExpressiveChatBubble(
                                message = message,
                                isMe = isMe,
                                mediaDir = mediaDir,
                                onImageClick = { fullScreenImageFile = it }
                            )
                        }
                    }
                }
            }

            // Material 3 Expressive Import Progress Banner
            AnimatedVisibility(
                visible = importState is ImportUiState.Loading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                val stepText = (importState as? ImportUiState.Loading)?.step ?: "Importing chat..."
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stepText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Error Alert Dialog
            (importState as? ImportUiState.Error)?.let { err ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissImportState() },
                    icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Import Error") },
                    text = { Text(err.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissImportState() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Centered pill header indicating date transitions.
 */
@Composable
fun DateSeparatorHeader(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
            tonalElevation = 1.dp
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * WhatsApp-style System Notification Message (e.g. End-to-end encryption, group events).
 */
@Composable
fun SystemMessageChip(content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Material 3 Expressive Chat Bubble with adaptive light/dark mode WhatsApp colors,
 * asymmetric corner radiuses, and memory-safe media rendering.
 */
@Composable
fun ExpressiveChatBubble(
    message: MessageEntity,
    isMe: Boolean,
    mediaDir: File,
    onImageClick: (File) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Adaptive WhatsApp Material 3 Expressive palette
    val bubbleColor = when {
        isMe && !isDark -> Color(0xFFE7FFDB) // WhatsApp Expressive Light Green
        isMe && isDark -> Color(0xFF005C4B)  // WhatsApp Expressive Dark Teal
        !isMe && !isDark -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val bubbleTextColor = when {
        isMe && isDark -> Color(0xFFE9EDEF)
        isMe && !isDark -> Color(0xFF111B21)
        !isMe && isDark -> Color(0xFFE9EDEF)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val timeColor = if (isDark) Color(0xFF8696A0) else Color(0xFF667781)

    // Expressive Asymmetrical Corner Radii (rounded with distinct speech tail)
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // Sender label for incoming group messages
                if (!isMe && message.sender.isNotBlank()) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Media rendering (Photos or Attachments)
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
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 260.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(file) }
                        )
                    } else {
                        NonImageAttachmentCard(
                            file = file,
                            filename = filename,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                // Message Text Content
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
                    Text(
                        text = contentToShow,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bubbleTextColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // Timestamp and Read Receipts
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeOnly = if (message.timestamp.contains(",")) {
                        message.timestamp.substringAfter(",").trim()
                    } else {
                        message.timestamp
                    }

                    Text(
                        text = timeOnly,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = timeColor
                    )

                    if (isMe) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read receipt",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF53BDEB) // WhatsApp Blue ticks
                        )
                    }
                }
            }
        }
    }
}

/**
 * Asynchronously decodes downsampled bitmaps and caches them in BitmapMemoryCache to eliminate UI lag.
 */
@Composable
fun LocalImage(file: File, modifier: Modifier = Modifier) {
    val cachedBitmap = remember(file.absolutePath) {
        BitmapMemoryCache.get(file.absolutePath)
    }

    val bitmapState = produceState<ImageBitmap?>(initialValue = cachedBitmap, key1 = file.absolutePath) {
        if (value == null) {
            val decoded = withContext(Dispatchers.IO) {
                decodeSampledBitmap(file, reqWidth = 600, reqHeight = 600)
            }
            decoded?.let {
                BitmapMemoryCache.put(file.absolutePath, it)
                value = it
            }
        }
    }

    bitmapState.value?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Attached photo",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Material 3 Expressive Card for documents, audio, videos, and general attachments.
 */
@Composable
fun NonImageAttachmentCard(file: File, filename: String, modifier: Modifier = Modifier) {
    val lower = filename.lowercase()
    val icon = when {
        lower.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        lower.endsWith(".mp3") || lower.endsWith(".opus") || lower.endsWith(".m4a") || lower.endsWith(".wav") -> Icons.Default.AudioFile
        lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv") || lower.endsWith(".mov") -> Icons.Default.VideoFile
        lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") -> Icons.Default.Description
        else -> Icons.Default.AttachFile
    }

    val fileSizeText = remember(file) {
        if (file.exists()) formatFileSize(file.length()) else ""
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fileSizeText.isNotEmpty()) {
                    Text(
                        text = fileSizeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen image preview modal.
 */
@Composable
fun FullScreenImageDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(file.absolutePath) {
                BitmapMemoryCache.get(file.absolutePath) ?: decodeSampledBitmap(file, 1200, 1200)
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Fullscreen preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Identity Selection Dialog.
 */
@Composable
fun IdentitySelectionDialog(
    senders: List<String>,
    currentSelected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Person, contentDescription = null) },
        title = { Text("Select Your Identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Choose which participant represents you to align outgoing chat bubbles correctly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                senders.forEach { name ->
                    val isSelected = name == currentSelected
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Material 3 Expressive Empty State view.
 */
@Composable
fun ExpressiveEmptyState(onImportClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "No Chats Imported Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Import your exported WhatsApp chat (.txt or .zip) to view conversations and media attachments offline with authentic styling.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("💡 How to export from WhatsApp:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("1. In WhatsApp, open the chat > tap ⋮ > More > Export chat", style = MaterialTheme.typography.labelSmall)
                    Text("2. Select 'Attach Media' (.zip) or 'Without Media' (.txt)", style = MaterialTheme.typography.labelSmall)
                    Text("3. Tap 'Import Chat File' below to browse", style = MaterialTheme.typography.labelSmall)
                }
            }

            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Chat File", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
