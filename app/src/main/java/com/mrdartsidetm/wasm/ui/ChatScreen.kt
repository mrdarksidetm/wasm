package com.mrdartsidetm.wasm.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrdartsidetm.wasm.R
import com.mrdartsidetm.wasm.data.MessageEntity
import com.mrdartsidetm.wasm.data.WhatsAppConversationEntity
import com.mrdartsidetm.wasm.ui.theme.SfProBold
import com.mrdartsidetm.wasm.ui.theme.SfProBoldItalic
import com.mrdartsidetm.wasm.ui.theme.SfProItalic
import com.mrdartsidetm.wasm.ui.theme.SfProSemibold
import com.mrdartsidetm.wasm.ui.theme.SfProRegular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

// Material 3 Expressive Container Color backward-compatibility extensions
val ColorScheme.surfaceContainerLowest: Color
    get() = surface.copy(alpha = 0.95f)

val ColorScheme.surfaceContainerLow: Color
    get() = surfaceVariant.copy(alpha = 0.5f)

val ColorScheme.surfaceContainer: Color
    get() = surfaceVariant.copy(alpha = 0.7f)

val ColorScheme.surfaceContainerHigh: Color
    get() = surfaceVariant.copy(alpha = 0.85f)

val ColorScheme.surfaceContainerHighest: Color
    get() = surfaceVariant

private val WhatsAppAccentGreen = Color(0xFF25D366)

private val AvatarColorsList = listOf(
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
    Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688),
    Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFFF5722)
)

private fun getWhatsAppAvatarColor(name: String): Color {
    val hash = abs(name.hashCode())
    return AvatarColorsList[hash % AvatarColorsList.size]
}

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
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

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
fun ChatScreen(
    viewModel: ChatViewModel,
    onImportClick: () -> Unit,
    onBackToPlatformChooser: (() -> Unit)? = null
) {
    val conversations by viewModel.filteredConversations.collectAsStateWithLifecycle()
    val allConversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selectedConversationId by viewModel.selectedConversationId.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val allMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val senders by viewModel.uniqueSenders.collectAsStateWithLifecycle()
    val importState by viewModel.importUiState.collectAsStateWithLifecycle()

    var showIdentityDialog by remember { mutableStateOf(false) }
    var fullScreenImageFile by remember { mutableStateOf<File?>(null) }
    var conversationToDelete by remember { mutableStateOf<WhatsAppConversationEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Auto-prompt identity selection if messages exist but identity is not configured
    LaunchedEffect(senders, currentUser) {
        if (senders.isNotEmpty() && currentUser.isEmpty() && selectedConversationId != null) {
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

    // Single conversation delete confirmation dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Chat?") },
            text = { Text("Permanently delete conversation with \"${conv.title}\" and all its messages and media?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conv.id)
                        conversationToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear all WhatsApp chats confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All WhatsApp Chats?") },
            text = { Text("This will permanently remove all saved WhatsApp conversations and extracted media files from your local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllWhatsApp()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen image viewer dialog
    fullScreenImageFile?.let { file ->
        FullScreenImageDialog(file = file, onDismiss = { fullScreenImageFile = null })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedConversationId == null) {
            // Level 1: Conversations List or Empty State
            if (allConversations.isEmpty() && importState !is ImportUiState.Loading) {
                ExpressiveEmptyState(
                    onImportClick = onImportClick,
                    onBack = onBackToPlatformChooser,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                WhatsAppConversationsListScreen(
                    conversations = conversations,
                    allConversations = allConversations,
                    viewModel = viewModel,
                    onConversationClick = { id -> viewModel.selectConversation(id) },
                    onDeleteConversation = { conv -> conversationToDelete = conv },
                    onClearAll = { showClearAllDialog = true },
                    onImportClick = onImportClick,
                    onBack = onBackToPlatformChooser
                )
            }
        } else {
            // Level 2: Individual Chat Detail View
            val chatMediaDir = viewModel.getMediaDirForConversation(activeConversation)
            WhatsAppChatDetailScreen(
                conversation = activeConversation,
                messages = messages,
                allMessages = allMessages,
                currentUser = currentUser,
                senders = senders,
                chatMediaDir = chatMediaDir,
                viewModel = viewModel,
                onBack = { viewModel.closeConversation() },
                onSwitchIdentity = { showIdentityDialog = true },
                onDeleteThisChat = { activeConversation?.let { conversationToDelete = it } },
                onImageClick = { fullScreenImageFile = it }
            )
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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

/**
 * WhatsApp Conversations List Screen (Inbox view showing every individual saved chat).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsAppConversationsListScreen(
    conversations: List<WhatsAppConversationEntity>,
    allConversations: List<WhatsAppConversationEntity>,
    viewModel: ChatViewModel,
    onConversationClick: (String) -> Unit,
    onDeleteConversation: (WhatsAppConversationEntity) -> Unit,
    onClearAll: () -> Unit,
    onImportClick: () -> Unit,
    onBack: (() -> Unit)?
) {
    val searchQuery by viewModel.conversationSearchQuery.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setConversationSearchQuery(it) },
                            placeholder = { Text("Search chats...") },
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
                            viewModel.setConversationSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setConversationSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to platform selection")
                            }
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_whatsapp),
                                contentDescription = null,
                                tint = WhatsAppAccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "WhatsApp Chats",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${allConversations.size} saved ${if (allConversations.size == 1) "chat" else "chats"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search chats")
                        }
                        FilledTonalButton(
                            onClick = onImportClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import", style = MaterialTheme.typography.labelMedium)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear All WhatsApp Chats") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onClearAll()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Import Chat", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        if (conversations.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chats found matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    WhatsAppConversationCard(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) },
                        onDelete = { onDeleteConversation(conversation) }
                    )
                }
            }
        }
    }
}

/**
 * Individual WhatsApp conversation card displayed in the conversations list.
 */
@Composable
private fun WhatsAppConversationCard(
    conversation: WhatsAppConversationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initial Avatar Circle
            Surface(
                shape = CircleShape,
                color = getWhatsAppAvatarColor(conversation.title),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = conversation.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Conversation info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.lastTimestamp.isNotBlank()) {
                        Text(
                            text = conversation.lastTimestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (conversation.lastMessage.isNotBlank()) conversation.lastMessage else "No messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WhatsAppAccentGreen.copy(alpha = 0.16f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "${conversation.messageCount} msgs",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WhatsAppAccentGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete single conversation button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * WhatsApp Individual Chat Detail Screen (viewing messages of a single selected conversation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsAppChatDetailScreen(
    conversation: WhatsAppConversationEntity?,
    messages: List<MessageEntity>,
    allMessages: List<MessageEntity>,
    currentUser: String,
    senders: List<String>,
    chatMediaDir: File,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSwitchIdentity: () -> Unit,
    onDeleteThisChat: () -> Unit,
    onImageClick: (File) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isDark = isSystemInDarkTheme()

    Scaffold(
        containerColor = if (isDark) Color(0xFF0B141A) else Color(0xFFEFEAE2),
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search in chat...") },
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
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to chats")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = getWhatsAppAvatarColor(conversation?.title ?: "Chat"),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val initial = conversation?.title?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = conversation?.title ?: "WhatsApp Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                IconButton(onClick = onSwitchIdentity) {
                                    Icon(Icons.Default.Person, contentDescription = "Switch User Identity")
                                }
                            }
                        }
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
                                        onSwitchIdentity()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete This Chat") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeleteThisChat()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val currentDate = extractDate(message.timestamp)
                    val prevMessage = if (index > 0) messages[index - 1] else null
                    val prevDate = prevMessage?.let { extractDate(it.timestamp) } ?: ""
                    val isDateBreak = currentDate != prevDate && currentDate.isNotEmpty()

                    if (isDateBreak) {
                        DateSeparatorHeader(date = currentDate)
                    }

                    if (message.isSystemMessage) {
                        SystemMessageChip(content = message.content)
                    } else {
                        val isMe = message.sender == currentUser

                        // Grouping rule:
                        // Sender name is shown only on the first message of a consecutive sender sequence.
                        // Exception: When a message crosses another day break, show the sender name again on the first message.
                        val isFirstInGroup = prevMessage == null ||
                                prevMessage.isSystemMessage ||
                                prevMessage.sender != message.sender ||
                                isDateBreak

                        val nextMessage = if (index < messages.size - 1) messages[index + 1] else null
                        val nextDate = nextMessage?.let { extractDate(it.timestamp) } ?: ""
                        val isNextDateBreak = nextDate != currentDate && nextDate.isNotEmpty()
                        val isLastInGroup = nextMessage == null ||
                                nextMessage.isSystemMessage ||
                                nextMessage.sender != message.sender ||
                                isNextDateBreak

                        val showSenderName = !isMe && message.sender.isNotBlank() && isFirstInGroup

                        ExpressiveChatBubble(
                            message = message,
                            isMe = isMe,
                            showSenderName = showSenderName,
                            isFirstInGroup = isFirstInGroup,
                            isLastInGroup = isLastInGroup,
                            mediaDir = chatMediaDir,
                            onImageClick = onImageClick
                        )
                    }
                }
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Material 3 Expressive Chat Bubble for WhatsApp messages.
 * Groups consecutive messages from the same sender with compact spacing and dynamic corners,
 * and displays the sender name only on the first message of a turn (or after a day break).
 */
@Composable
fun ExpressiveChatBubble(
    message: MessageEntity,
    isMe: Boolean,
    showSenderName: Boolean = false,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    mediaDir: File,
    onImageClick: (File) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Authentic WhatsApp speech bubble colors
    val bubbleColor = if (isMe) {
        if (isDark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
    } else {
        if (isDark) Color(0xFF202C33) else Color(0xFFFFFFFF)
    }

    val contentTextColor = if (isDark) {
        Color(0xFFE9EDEF)
    } else {
        Color(0xFF111B21)
    }

    val timestampColor = if (isDark) {
        Color(0xFF8696A0)
    } else {
        Color(0xFF667781)
    }

    // Material 3 Expressive corner grouping for individual bubbles
    val bubbleShape = if (isMe) {
        when {
            isFirstInGroup && isLastInGroup -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            isFirstInGroup -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 6.dp)
            isLastInGroup -> RoundedCornerShape(topStart = 16.dp, topEnd = 6.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
            else -> RoundedCornerShape(topStart = 16.dp, topEnd = 6.dp, bottomStart = 16.dp, bottomEnd = 6.dp)
        }
    } else {
        when {
            isFirstInGroup && isLastInGroup -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            isFirstInGroup -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 16.dp)
            isLastInGroup -> RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
            else -> RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 16.dp)
        }
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bottomSpacing = if (isLastInGroup) 6.dp else 2.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(bottom = bottomSpacing),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 72.dp, max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                if (showSenderName) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = SfProBold),
                        fontWeight = FontWeight.Bold,
                        color = getWhatsAppAvatarColor(message.sender),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Render media attachment if present
                if (!message.mediaName.isNullOrBlank()) {
                    val mediaFile = File(mediaDir, message.mediaName)
                    MediaAttachmentCard(
                        file = mediaFile,
                        fileName = message.mediaName,
                        onImageClick = onImageClick
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Render text if not just a file placeholder
                val isRedundantPlaceholder = !message.mediaName.isNullOrBlank() &&
                        (message.content.equals("${message.mediaName} (file attached)", ignoreCase = true) ||
                         message.content.equals("<attached: ${message.mediaName}>", ignoreCase = true))

                if (!isRedundantPlaceholder && message.content.isNotBlank()) {
                    WhatsAppFormattedMessage(
                        content = message.content,
                        textColor = contentTextColor
                    )
                }

                // Timestamp and read receipt
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeOnly = message.timestamp.substringAfter(',').trim()
                    Text(
                        text = timeOnly,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = timestampColor
                    )
                    if (isMe) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read",
                            tint = Color(0xFF53BDEB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders WhatsApp-style rich formatted messages:
 * - Bold: *text*
 * - Italic: _text_
 * - Strikethrough: ~text~
 * - Monospace: ```text```
 * - Inline code: `text`
 * - Grouped / nested combinations: e.g. *_bold and italic_*
 * - Quote block: > quote text (renders with vertical accent stripe)
 * - Bulleted list: * item or - item (renders with bullet points)
 * - Numbered list: 1. item (renders with aligned numbers)
 */
@Composable
fun WhatsAppFormattedMessage(
    content: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val lines = remember(content) { content.lines() }

    val hasBlockElements = remember(lines) {
        lines.any { line ->
            line.startsWith("> ") ||
            line.startsWith("* ") ||
            line.startsWith("- ") ||
            line.matches(Regex("""^\d+\.\s.*"""))
        }
    }

    if (!hasBlockElements) {
        val annotated = remember(content, isDark, textColor) {
            parseWhatsAppInlineFormattedText(content, isDark, textColor)
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            lines.forEach { rawLine ->
                when {
                    rawLine.startsWith("> ") -> {
                        val quoteText = rawLine.removePrefix("> ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .padding(vertical = 2.dp)
                                    .height(18.dp)
                                    .background(
                                        color = WhatsAppAccentGreen,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseWhatsAppInlineFormattedText(quoteText, isDark, textColor),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = SfProItalic,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = textColor.copy(alpha = 0.88f)
                            )
                        }
                    }
                    rawLine.startsWith("* ") || rawLine.startsWith("- ") -> {
                        val itemText = rawLine.substring(2)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SfProBold),
                                fontWeight = FontWeight.Bold,
                                color = WhatsAppAccentGreen
                            )
                            Text(
                                text = parseWhatsAppInlineFormattedText(itemText, isDark, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                    }
                    rawLine.matches(Regex("""^\d+\.\s.*""")) -> {
                        val numPrefix = rawLine.substringBefore(". ") + ". "
                        val itemText = rawLine.substringAfter(". ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = numPrefix,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SfProSemibold),
                                fontWeight = FontWeight.SemiBold,
                                color = WhatsAppAccentGreen
                            )
                            Text(
                                text = parseWhatsAppInlineFormattedText(itemText, isDark, textColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = parseWhatsAppInlineFormattedText(rawLine, isDark, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses inline formatting tags for WhatsApp messages recursively to support nesting:
 * e.g., *_bold and italic_*, *~bold strike~*, `code`, ```monospace```.
 * Uses dedicated San Francisco Pro FontFamily definitions for authentic bold and italic typography.
 */
fun parseWhatsAppInlineFormattedText(
    text: String,
    isDark: Boolean,
    textColor: Color = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val codeBackground = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)

    data class ActiveFormat(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false
    )

    fun getSpanStyle(active: ActiveFormat): SpanStyle {
        val family = when {
            active.bold && active.italic -> SfProBoldItalic
            active.bold -> SfProBold
            active.italic -> SfProItalic
            else -> null
        }
        val weight = if (active.bold) FontWeight.Bold else null
        val style = if (active.italic) FontStyle.Italic else null
        val decoration = if (active.strike) TextDecoration.LineThrough else null

        return SpanStyle(
            fontFamily = family,
            fontWeight = weight,
            fontStyle = style,
            textDecoration = decoration,
            color = textColor
        )
    }

    fun parse(segment: String, active: ActiveFormat) {
        var i = 0
        while (i < segment.length) {
            // Triple backtick monospace: ```text```
            if (segment.startsWith("```", i)) {
                val closeIdx = segment.indexOf("```", i + 3)
                if (closeIdx != -1) {
                    val inner = segment.substring(i + 3, closeIdx)
                    val start = builder.length
                    builder.append(inner)
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = textColor
                        ),
                        start,
                        builder.length
                    )
                    i = closeIdx + 3
                    continue
                }
            }

            // Single backtick inline code: `text`
            if (segment[i] == '`') {
                val closeIdx = segment.indexOf('`', i + 1)
                if (closeIdx != -1) {
                    val inner = segment.substring(i + 1, closeIdx)
                    val start = builder.length
                    builder.append(inner)
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = textColor
                        ),
                        start,
                        builder.length
                    )
                    i = closeIdx + 1
                    continue
                }
            }

            // Bold: *text*
            if (segment[i] == '*') {
                val closeIdx = findClosingDelimiter(segment, i, '*')
                if (closeIdx != -1) {
                    val inner = segment.substring(i + 1, closeIdx)
                    val start = builder.length
                    parse(inner, active.copy(bold = true))
                    val end = builder.length
                    if (end > start) {
                        builder.addStyle(getSpanStyle(active.copy(bold = true)), start, end)
                    }
                    i = closeIdx + 1
                    continue
                }
            }

            // Italic: _text_
            if (segment[i] == '_') {
                val closeIdx = findClosingDelimiter(segment, i, '_')
                if (closeIdx != -1) {
                    val inner = segment.substring(i + 1, closeIdx)
                    val start = builder.length
                    parse(inner, active.copy(italic = true))
                    val end = builder.length
                    if (end > start) {
                        builder.addStyle(getSpanStyle(active.copy(italic = true)), start, end)
                    }
                    i = closeIdx + 1
                    continue
                }
            }

            // Strikethrough: ~text~
            if (segment[i] == '~') {
                val closeIdx = findClosingDelimiter(segment, i, '~')
                if (closeIdx != -1) {
                    val inner = segment.substring(i + 1, closeIdx)
                    val start = builder.length
                    parse(inner, active.copy(strike = true))
                    val end = builder.length
                    if (end > start) {
                        builder.addStyle(getSpanStyle(active.copy(strike = true)), start, end)
                    }
                    i = closeIdx + 1
                    continue
                }
            }

            // Plain text character
            val charStart = builder.length
            builder.append(segment[i])
            if (active.bold || active.italic || active.strike) {
                builder.addStyle(getSpanStyle(active), charStart, builder.length)
            }
            i++
        }
    }

    parse(text, ActiveFormat())
    return builder.toAnnotatedString()
}

private fun findClosingDelimiter(text: String, startIndex: Int, delimiter: Char): Int {
    if (startIndex + 1 >= text.length || text[startIndex + 1].isWhitespace()) return -1
    for (j in (startIndex + 1) until text.length) {
        if (text[j] == delimiter && j > startIndex + 1) {
            if (!text[j - 1].isWhitespace()) {
                return j
            }
        }
    }
    return -1
}

/**
 * Attachment card rendering photos, audio, documents with memory caching.
 */
@Composable
fun MediaAttachmentCard(
    file: File,
    fileName: String,
    onImageClick: (File) -> Unit
) {
    val isImage = fileName.endsWith(".jpg", true) ||
            fileName.endsWith(".jpeg", true) ||
            fileName.endsWith(".png", true) ||
            fileName.endsWith(".webp", true)

    if (isImage && file.exists()) {
        val cached = BitmapMemoryCache.get(file.absolutePath)
        val bitmapState = produceState(initialValue = cached, key1 = file.absolutePath) {
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

        bitmapState.value?.let { imgBitmap ->
            Image(
                bitmap = imgBitmap,
                contentDescription = fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(file) }
            )
        }
    } else {
        // Non-image file attachment card (Audio, PDF, Archive, Video)
        val isAudio = fileName.endsWith(".mp3", true) || fileName.endsWith(".opus", true) || fileName.endsWith(".m4a", true) || fileName.endsWith(".aac", true)
        val isPdf = fileName.endsWith(".pdf", true)
        val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".3gp", true)

        val icon = when {
            isAudio -> Icons.Default.Audiotrack
            isPdf -> Icons.Default.PictureAsPdf
            isVideo -> Icons.Default.VideoFile
            else -> Icons.Default.InsertDriveFile
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
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
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (file.exists()) {
                        Text(
                            text = formatFileSize(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fullscreen image preview modal dialog.
 */
@Composable
fun FullScreenImageDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(file.absolutePath) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Identity Selection Dialog for choosing who "Me" is.
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
        title = { Text("Who are you in this chat?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select your name so we can align your messages to the right side:",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(senders) { name ->
                        val isSelected = name == currentSelected
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(name) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = getWhatsAppAvatarColor(name),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
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
 * Material 3 Expressive Empty State view for WhatsApp.
 */
@Composable
fun ExpressiveEmptyState(
    onImportClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                color = WhatsAppAccentGreen.copy(alpha = 0.16f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_whatsapp),
                        contentDescription = "WhatsApp",
                        tint = WhatsAppAccentGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Text(
                text = "No WhatsApp Chats Saved",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Import any WhatsApp export file (.txt or .zip with attachments). Every individual chat is saved separately so you can browse all your conversations offline.",
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
                    Text("1. In WhatsApp, open any chat > tap ⋮ > More > Export chat", style = MaterialTheme.typography.labelSmall)
                    Text("2. Select 'Attach Media' (.zip) or 'Without Media' (.txt)", style = MaterialTheme.typography.labelSmall)
                    Text("3. Tap 'Import Chat File' below to save it into Wasm", style = MaterialTheme.typography.labelSmall)
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

            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back to Platform Selection")
                }
            }
        }
    }
}
