package com.mrdartsidetm.wasm.ui.instagram

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.mrdartsidetm.wasm.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrdartsidetm.wasm.data.InstagramAccountEntity
import com.mrdartsidetm.wasm.data.InstagramConversationEntity
import com.mrdartsidetm.wasm.data.InstagramMessageEntity
import com.mrdartsidetm.wasm.ui.BitmapMemoryCache
import com.mrdartsidetm.wasm.ui.DateSeparatorHeader
import com.mrdartsidetm.wasm.ui.FullScreenImageDialog
import com.mrdartsidetm.wasm.ui.ImportUiState
import com.mrdartsidetm.wasm.ui.decodeSampledBitmap
import com.mrdartsidetm.wasm.ui.extractDate
import com.mrdartsidetm.wasm.ui.formatFileSize
import com.mrdartsidetm.wasm.util.AudioPlayerManager
import com.mrdartsidetm.wasm.util.GalleryDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

// Instagram Direct vibrant gradients & colors
val InstagramPurple = Color(0xFF833AB4)
val InstagramPink = Color(0xFFC13584)
val InstagramRed = Color(0xFFE1306C)
val InstagramOrange = Color(0xFFFD1D1D)
val InstagramYellow = Color(0xFFFCAF45)
val InstagramDirectBlue = Color(0xFF3797EF)

val InstagramGradient = Brush.horizontalGradient(
    listOf(InstagramPurple, InstagramPink, InstagramRed, InstagramOrange, InstagramYellow)
)

val InstagramDmOutgoingGradient = Brush.linearGradient(
    listOf(Color(0xFF6B52D9), Color(0xFF8B36B2), Color(0xFFB82775))
)

val AvatarColors = listOf(
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
    Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688),
    Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFFF5722)
)

fun getAvatarColor(name: String): Color {
    val hash = abs(name.hashCode())
    return AvatarColors[hash % AvatarColors.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramScreen(
    viewModel: InstagramViewModel,
    onImportClick: () -> Unit,
    onBackToPlatformChooser: (() -> Unit)? = null
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val importState by viewModel.importUiState.collectAsStateWithLifecycle()
    val selectedConversationId by viewModel.selectedConversationId.collectAsStateWithLifecycle()
    val isViewingDetails by viewModel.isViewingConversationDetails.collectAsStateWithLifecycle()
    val expandedMediaList by viewModel.activeExpandedMediaList.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    var fullScreenImageFile by remember { mutableStateOf<File?>(null) }
    var activeVideoFile by remember { mutableStateOf<File?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Instagram Data?") },
            text = { Text("This will permanently remove all imported Instagram messages, conversations, and media.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearInstagramData()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    fullScreenImageFile?.let { file ->
        FullScreenImageDialog(file = file, onDismiss = { fullScreenImageFile = null })
    }

    activeVideoFile?.let { file ->
        InstagramVideoPlayerDialog(file = file, onDismiss = { activeVideoFile = null })
    }

    // Determine current screen index
    val currentStep = when {
        account == null -> 0 // Empty Import State
        !account!!.isDived -> 1 // Landing Page
        expandedMediaList != null -> 4 // Expanded Media Viewer Page (vertical scrollable + 3-dots download)
        isViewingDetails -> 5 // Conversation Details / 3-Column Media Gallery Page
        selectedConversationId == null -> 2 // Conversations / Inbox List
        else -> 3 // Conversation DM Thread View
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(animationSpec = tween(350)) { width -> width } + fadeIn(animationSpec = tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> -width } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(350)) { width -> -width } + fadeIn(animationSpec = tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> width } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "InstagramFlowTransition"
        ) { step ->
            when (step) {
                0 -> InstagramEmptyState(
                    onImportClick = onImportClick,
                    onBack = onBackToPlatformChooser,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> account?.let { acc ->
                    InstagramLandingPage(
                        account = acc,
                        onDiveClick = { viewModel.diveIn() },
                        onReimportClick = onImportClick,
                        onClearClick = { showClearDialog = true },
                        onBack = onBackToPlatformChooser
                    )
                }
                2 -> account?.let { acc ->
                    InstagramConversationsList(
                        account = acc,
                        viewModel = viewModel,
                        onConversationClick = { id -> viewModel.selectConversation(id) },
                        onBackToLanding = { viewModel.backToLanding() },
                        onReimportClick = onImportClick,
                        onClearClick = { showClearDialog = true }
                    )
                }
                3 -> InstagramDmThreadScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeConversation() },
                    onOpenDetails = { viewModel.openConversationDetails() },
                    onImageClick = { fullScreenImageFile = it },
                    onVideoClick = { activeVideoFile = it },
                    onOpenGroupMedia = { list, title -> viewModel.openExpandedMedia(list, title) }
                )
                4 -> expandedMediaList?.let { list ->
                    InstagramExpandedMediaViewerScreen(
                        mediaList = list,
                        title = viewModel.activeMediaViewerTitle.collectAsStateWithLifecycle().value,
                        onBack = { viewModel.closeExpandedMedia() },
                        onPlayVideo = { activeVideoFile = it }
                    )
                }
                5 -> InstagramConversationDetailsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeConversationDetails() },
                    onMediaClick = { list, title -> viewModel.openExpandedMedia(list, title) }
                )
            }
        }

        // Import Progress Overlay
        AnimatedVisibility(
            visible = importState is ImportUiState.Loading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            val stepText = (importState as? ImportUiState.Loading)?.step ?: "Importing Instagram data..."
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

        // Error Dialog
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
 * Empty State inviting user to import an Instagram ZIP export.
 */
@Composable
fun InstagramEmptyState(
    onImportClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(InstagramGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_instagram),
                contentDescription = "Instagram",
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Instagram Messages",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Import your Meta data export (ZIP) to browse direct messages, photos, videos, voice notes, stickers, and group media galleries offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onImportClick,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.FolderZip, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import Instagram ZIP", fontWeight = FontWeight.SemiBold)
        }

        if (onBack != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back to Platform Selection")
            }
        }
    }
}

/**
 * Landing Page showing the Account Name with the prominent "Let's Dive" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramLandingPage(
    account: InstagramAccountEntity,
    onDiveClick: () -> Unit,
    onReimportClick: () -> Unit,
    onClearClick: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Platform Selection")
                        }
                    }
                },
                title = {
                    Text(
                        "Instagram Export",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Another ZIP") },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onReimportClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Data") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onClearClick()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.height(16.dp))

                // Instagram Profile Header Ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(InstagramGradient)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "@${account.accountName}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                if (account.displayName.isNotBlank() && account.displayName != account.accountName) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (account.exportDate.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Exported: ${account.exportDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = InstagramPurple)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${account.totalConversations}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Conversations", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = InstagramPink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${account.totalMessages}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Messages", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaFeatureBadge(Icons.Default.Image, "Photos")
                        MediaFeatureBadge(Icons.Default.Videocam, "Videos")
                        MediaFeatureBadge(Icons.Default.Mic, "Voices")
                        MediaFeatureBadge(Icons.Default.Mood, "Stickers")
                    }
                }
            }

            // Big "Let's Dive" Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(InstagramGradient)
                    .clickable(onClick = onDiveClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Let's Dive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MediaFeatureBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Instagram Inbox / Conversations list screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramConversationsList(
    account: InstagramAccountEntity,
    viewModel: InstagramViewModel,
    onConversationClick: (String) -> Unit,
    onBackToLanding: () -> Unit,
    onReimportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val conversations by viewModel.filteredConversations.collectAsStateWithLifecycle()
    val allConversations by viewModel.conversations.collectAsStateWithLifecycle()
    val filter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var isSearchOpen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSearchOpen) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search conversations...") },
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
                            isSearchOpen = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = account.accountName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${allConversations.size} conversations",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToLanding) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Overview")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Overview") },
                                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onBackToLanding()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import ZIP") },
                                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onReimportClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear All") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onClearClick()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = filter.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = filter == InstagramConversationFilter.ALL,
                    onClick = { viewModel.setFilter(InstagramConversationFilter.ALL) },
                    text = { Text("All (${allConversations.size})") }
                )
                Tab(
                    selected = filter == InstagramConversationFilter.INBOX,
                    onClick = { viewModel.setFilter(InstagramConversationFilter.INBOX) },
                    text = { Text("Inbox (${allConversations.count { !it.isRequest }})") }
                )
                val requestCount = allConversations.count { it.isRequest }
                Tab(
                    selected = filter == InstagramConversationFilter.REQUESTS,
                    onClick = { viewModel.setFilter(InstagramConversationFilter.REQUESTS) },
                    text = { Text("Requests ($requestCount)") }
                )
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No conversations match \"$searchQuery\"" else "No conversations found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(conversations, key = { it.id }) { item ->
                        InstagramConversationListItem(
                            item = item,
                            onClick = { onConversationClick(item.id) }
                        )
                        Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramConversationListItem(
    item: InstagramConversationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(getAvatarColor(item.title)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.title.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (item.lastTimestamp.isNotBlank()) {
                    Text(
                        text = formatShortTime(item.lastTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.lastMessage.isNotBlank()) item.lastMessage else "${item.messageCount} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (item.isRequest) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "Request",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatShortTime(timestamp: String): String {
    val commaIdx = timestamp.indexOf(',')
    return if (commaIdx != -1) {
        timestamp.substring(0, commaIdx)
    } else {
        timestamp
    }
}

/**
 * Instagram Direct Message Thread View.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramDmThreadScreen(
    viewModel: InstagramViewModel,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onImageClick: (File) -> Unit,
    onVideoClick: (File) -> Unit,
    onOpenGroupMedia: (List<Pair<String, File>>, String) -> Unit
) {
    val conversation by viewModel.currentConversation.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.conversationSearchQuery.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setConversationSearchQuery(it) },
                            placeholder = { Text("Search in conversation...") },
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
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        // Tapping the username opens the Conversation Details & Media Gallery page
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenDetails)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(getAvatarColor(conversation?.title ?: "")),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (conversation?.title ?: "").take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = conversation?.title ?: "Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${messages.size} messages • Tap for media",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Inbox")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        IconButton(onClick = onOpenDetails) {
                            Icon(Icons.Default.Info, contentDescription = "Conversation Details")
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
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    var previousDate = ""
                    items(messages, key = { it.id }) { message ->
                        val currentDate = extractDate(message.timestamp)
                        if (currentDate != previousDate && currentDate.isNotEmpty()) {
                            DateSeparatorHeader(date = currentDate)
                            previousDate = currentDate
                        }

                        val resolvedMediaList = remember(message.mediaPaths, message.mediaPath) {
                            viewModel.resolveMessageMedia(message)
                        }

                        InstagramChatBubble(
                            message = message,
                            mediaList = resolvedMediaList,
                            audioPlayerManager = viewModel.audioPlayerManager,
                            onImageClick = { onImageClick(it) },
                            onVideoClick = { onVideoClick(it) },
                            onOpenGroupMedia = { list ->
                                onOpenGroupMedia(list, "${conversation?.title ?: "Chat"} Media")
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Authentic Instagram DM Speech Bubble with collage grouping for simultaneous photos/videos.
 */
@Composable
fun InstagramChatBubble(
    message: InstagramMessageEntity,
    mediaList: List<Pair<String, File>>,
    audioPlayerManager: AudioPlayerManager,
    onImageClick: (File) -> Unit,
    onVideoClick: (File) -> Unit,
    onOpenGroupMedia: (List<Pair<String, File>>) -> Unit
) {
    val isMe = message.isOutgoing
    val isDark = isSystemInDarkTheme()

    val bubbleAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 4.dp,
            bottomEnd = 18.dp
        )
    }

    val incomingBg = if (isDark) Color(0xFF262626) else Color(0xFFEFEFEF)
    val incomingTextColor = if (isDark) Color.White else Color(0xFF1C1E21)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = bubbleAlignment
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(bubbleShape)
                    .then(
                        if (isMe) {
                            Modifier.background(InstagramDmOutgoingGradient)
                        } else {
                            Modifier.background(incomingBg)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Sender name
                if (!isMe && message.sender.isNotBlank()) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = getAvatarColor(message.sender),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Grouped Photos & Videos Collage (Single or Multi-item grid)
                val visualMedia = mediaList.filter { it.first == "photo" || it.first == "video" || it.first == "gif" }
                if (visualMedia.isNotEmpty()) {
                    InstagramMediaCollage(
                        mediaList = visualMedia,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onExpandGroup = { onOpenGroupMedia(visualMedia) }
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Voice notes (Audio)
                val audioMedia = mediaList.filter { it.first == "audio" }
                for ((_, audioFile) in audioMedia) {
                    InstagramAudioItem(file = audioFile, audioPlayerManager = audioPlayerManager, isMe = isMe)
                    Spacer(Modifier.height(4.dp))
                }

                // Shared Reel or Link
                if (message.sharedUrl != null) {
                    InstagramSharedLinkItem(
                        url = message.sharedUrl,
                        title = message.sharedTitle ?: message.content,
                        isReel = message.mediaType == "reel",
                        isMe = isMe
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Text Content
                if (message.content.isNotBlank() && (mediaList.isEmpty() || message.content != message.sharedTitle)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMe) Color.White else incomingTextColor
                    )
                }

                // Timestamp
                if (message.timestamp.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        // Reaction pill underneath
        if (!message.reactions.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMe) 0.dp else 8.dp, end = if (isMe) 8.dp else 0.dp)
            ) {
                Text(
                    text = message.reactions,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Groups multiple photos/videos sent at the same time into an Instagram DM collage grid.
 */
@Composable
fun InstagramMediaCollage(
    mediaList: List<Pair<String, File>>,
    onImageClick: (File) -> Unit,
    onVideoClick: (File) -> Unit,
    onExpandGroup: () -> Unit
) {
    when (mediaList.size) {
        1 -> {
            val (type, file) = mediaList[0]
            if (type == "video") {
                InstagramVideoItem(file = file, onVideoClick = { onVideoClick(file) })
            } else if (type == "gif") {
                InstagramGifItem(file = file)
            } else {
                InstagramPhotoItem(file = file, onImageClick = { onImageClick(file) })
            }
        }
        2 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandGroup),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    InstagramCollageTile(media = mediaList[0])
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    InstagramCollageTile(media = mediaList[1])
                }
            }
        }
        3 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandGroup),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                    InstagramCollageTile(media = mediaList[0])
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        InstagramCollageTile(media = mediaList[1])
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        InstagramCollageTile(media = mediaList[2])
                    }
                }
            }
        }
        else -> {
            // 4+ media items: 2x2 grid with +N badge on 4th tile
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpandGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        InstagramCollageTile(media = mediaList[0])
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        InstagramCollageTile(media = mediaList[1])
                    }
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        InstagramCollageTile(media = mediaList[2])
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        InstagramCollageTile(media = mediaList[3])
                        if (mediaList.size > 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${mediaList.size - 3}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramCollageTile(media: Pair<String, File>) {
    val (type, file) = media
    val isVideo = type == "video"

    val bitmapState = produceState<ImageBitmap?>(initialValue = BitmapMemoryCache.get(file.absolutePath), key1 = file.absolutePath) {
        val cached = BitmapMemoryCache.get(file.absolutePath)
        if (cached != null) {
            value = cached
        } else {
            val bmp = withContext(Dispatchers.IO) {
                if (isVideo) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val b = retriever.getFrameAtTime(0)?.asImageBitmap()
                        retriever.release()
                        b
                    } catch (e: Throwable) {
                        null
                    }
                } else {
                    decodeSampledBitmap(file, reqWidth = 300, reqHeight = 300)
                }
            }
            bmp?.let {
                BitmapMemoryCache.put(file.absolutePath, it)
                value = it
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmapState.value?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Image, contentDescription = null, tint = Color.White)
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Expanded Media Viewer Page: Vertical scrollable view of all media with hold-to-select and 3-dots download.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstagramExpandedMediaViewerScreen(
    mediaList: List<Pair<String, File>>,
    title: String,
    onBack: () -> Unit,
    onPlayVideo: (File) -> Unit
) {
    val context = LocalContext.current
    val selectedIndices = remember { mutableStateListOf<Int>() }
    val isSelectionMode = selectedIndices.isNotEmpty()
    var showMenu by remember { mutableStateOf(false) }

    fun toggleSelection(index: Int) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index)
        } else {
            selectedIndices.add(index)
        }
    }

    fun downloadItems(indices: List<Int>) {
        val targets = if (indices.isEmpty()) mediaList else indices.map { mediaList[it] }
        val count = GalleryDownloader.saveBatchToGallery(context, targets)
        Toast.makeText(context, "Saved $count media to Gallery!", Toast.LENGTH_SHORT).show()
        selectedIndices.clear()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) "${selectedIndices.size} selected" else title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            selectedIndices.clear()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 3 dots menu on the right
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Download into Gallery") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    downloadItems(selectedIndices.toList())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedIndices.size == mediaList.size) "Deselect All" else "Select All") },
                                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (selectedIndices.size == mediaList.size) {
                                        selectedIndices.clear()
                                    } else {
                                        selectedIndices.clear()
                                        selectedIndices.addAll(mediaList.indices)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIndices.size} item(s) selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { downloadItems(selectedIndices.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = InstagramDirectBlue)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Gallery")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(mediaList) { index, (type, file) ->
                val isSelected = selectedIndices.contains(index)
                val isVideo = type == "video"

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.5.dp, InstagramDirectBlue) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onLongClick = {
                                toggleSelection(index)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(index)
                                } else if (isVideo) {
                                    onPlayVideo(file)
                                }
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            if (isVideo) {
                                InstagramVideoItem(file = file, onVideoClick = { onPlayVideo(file) })
                            } else if (type == "gif") {
                                InstagramGifItem(file = file)
                            } else {
                                InstagramPhotoItem(file = file, onImageClick = {
                                    if (isSelectionMode) toggleSelection(index)
                                })
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = formatFileSize(file.length()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Selection Badge
                        if (isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) InstagramDirectBlue else Color.Black.copy(alpha = 0.5f))
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Conversation Details Page: Username in middle, segmented tabs, and 3-column photo/video gallery grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramConversationDetailsScreen(
    viewModel: InstagramViewModel,
    onBack: () -> Unit,
    onMediaClick: (List<Pair<String, File>>, String) -> Unit
) {
    val conversation by viewModel.currentConversation.collectAsStateWithLifecycle()
    val mediaFiles by viewModel.allConversationMediaFiles.collectAsStateWithLifecycle()
    val voiceNotes by viewModel.allConversationVoiceNotes.collectAsStateWithLifecycle()
    val sharedLinks by viewModel.allConversationLinks.collectAsStateWithLifecycle()

    var selectedSegment by remember { mutableIntStateOf(0) } // 0: Media, 1: Voices, 2: Links

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Centered Header: Account Name & Avatar in the Middle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(getAvatarColor(conversation?.title ?: "")),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (conversation?.title ?: "").take(1).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = conversation?.title ?: "Chat Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${conversation?.messageCount ?: 0} messages • ${mediaFiles.size} media files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Segmented Section Tabs: Media, Voice Notes, Links
            TabRow(
                selectedTabIndex = selectedSegment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    text = { Text("Media (${mediaFiles.size})") }
                )
                Tab(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    text = { Text("Voices (${voiceNotes.size})") }
                )
                Tab(
                    selected = selectedSegment == 2,
                    onClick = { selectedSegment = 2 },
                    text = { Text("Links (${sharedLinks.size})") }
                )
            }

            when (selectedSegment) {
                0 -> {
                    // 3-Column Square Grid Gallery (horizontally 3, vertically as many media as there are)
                    if (mediaFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No photos or videos exchanged in this chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(mediaFiles.size) { index ->
                                val media = mediaFiles[index]
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            // Open expanded media viewer starting with all conversation media
                                            val reordered = mediaFiles.subList(index, mediaFiles.size) + mediaFiles.subList(0, index)
                                            onMediaClick(reordered, "${conversation?.title ?: "Chat"} Gallery")
                                        }
                                ) {
                                    InstagramCollageTile(media = media)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Voice Notes List
                    if (voiceNotes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No voice notes in this chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(voiceNotes) { msg ->
                                val mediaList = viewModel.resolveMessageMedia(msg)
                                val audioFile = mediaList.find { it.first == "audio" }?.second
                                if (audioFile != null) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "${msg.sender} • ${msg.timestamp}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            InstagramAudioItem(
                                                file = audioFile,
                                                audioPlayerManager = viewModel.audioPlayerManager,
                                                isMe = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Links & Reels List
                    if (sharedLinks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No shared reels or links in this chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sharedLinks) { msg ->
                                msg.sharedUrl?.let { url ->
                                    InstagramSharedLinkItem(
                                        url = url,
                                        title = msg.sharedTitle ?: msg.content,
                                        isReel = msg.mediaType == "reel",
                                        isMe = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Instagram Photo attachment item with downsampled caching.
 */
@Composable
fun InstagramPhotoItem(file: File, onImageClick: () -> Unit) {
    val bitmapState = produceState<ImageBitmap?>(initialValue = BitmapMemoryCache.get(file.absolutePath), key1 = file.absolutePath) {
        val cached = BitmapMemoryCache.get(file.absolutePath)
        if (cached != null) {
            value = cached
        } else {
            val decoded = withContext(Dispatchers.IO) {
                decodeSampledBitmap(file, reqWidth = 500, reqHeight = 500)
            }
            decoded?.let {
                BitmapMemoryCache.put(file.absolutePath, it)
                value = it
            }
        }
    }

    bitmapState.value?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = "Photo attachment",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onImageClick),
            contentScale = ContentScale.Crop
        )
    } ?: Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Gray.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
    }
}

/**
 * Instagram Video preview item with video thumbnail and play icon.
 */
@Composable
fun InstagramVideoItem(file: File, onVideoClick: () -> Unit) {
    val thumbnailState = produceState<ImageBitmap?>(initialValue = null, key1 = file.absolutePath) {
        val cached = BitmapMemoryCache.get("thumb_" + file.absolutePath)
        if (cached != null) {
            value = cached
        } else {
            val thumb = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val bmp = retriever.getFrameAtTime(0)
                    retriever.release()
                    bmp?.asImageBitmap()
                } catch (e: Throwable) {
                    null
                }
            }
            thumb?.let {
                BitmapMemoryCache.put("thumb_" + file.absolutePath, it)
                value = it
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onVideoClick),
        contentAlignment = Alignment.Center
    ) {
        thumbnailState.value?.let { thumb ->
            Image(
                bitmap = thumb,
                contentDescription = "Video preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        ) {
            Text(
                text = formatFileSize(file.length()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Instagram Voice Message Player with Play/Pause button, scrubber, and duration.
 */
@Composable
fun InstagramAudioItem(file: File, audioPlayerManager: AudioPlayerManager, isMe: Boolean) {
    val currentPath by audioPlayerManager.currentPlayingPath.collectAsStateWithLifecycle()
    val isPlaying by audioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val currentPos by audioPlayerManager.currentPositionMs.collectAsStateWithLifecycle()
    val duration by audioPlayerManager.durationMs.collectAsStateWithLifecycle()

    val isThisAudioPlaying = currentPath == file.absolutePath && isPlaying
    val thisPos = if (currentPath == file.absolutePath) currentPos else 0
    val thisDuration = if (currentPath == file.absolutePath && duration > 0) duration else 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { audioPlayerManager.togglePlay(file) },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMe) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = if (isThisAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause Voice",
                tint = if (isMe) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            val progress = (thisPos.toFloat() / thisDuration.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isMe) Color.White else MaterialTheme.colorScheme.primary,
                trackColor = if (isMe) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(thisPos),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimeMs(if (currentPath == file.absolutePath) duration else 0),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatTimeMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

/**
 * Instagram Animated GIF / Sticker item.
 */
@Composable
fun InstagramGifItem(file: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    try {
                        val source = ImageDecoder.createSource(file)
                        val drawable = ImageDecoder.decodeDrawable(source)
                        setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.start()
                        }
                    } catch (e: Throwable) {
                        // ignore decode errors
                    }
                }
            },
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        InstagramPhotoItem(file = file, onImageClick = {})
    }
}

/**
 * Shared Reel or Link preview card.
 */
@Composable
fun InstagramSharedLinkItem(url: String, title: String, isReel: Boolean, isMe: Boolean) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isMe) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // ignore intent errors
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isReel) Icons.Default.Movie else Icons.Default.Link,
                contentDescription = null,
                tint = if (isMe) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReel) "Instagram Reel" else "Shared Link",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) Color.White else MaterialTheme.colorScheme.primary
                )
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen Video Player Dialog using native Android VideoView & MediaController.
 */
@Composable
fun InstagramVideoPlayerDialog(file: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener {
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Video", tint = Color.White)
            }
        }
    }
}
