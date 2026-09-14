package com.mrdartsidetm.wasm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrdartsidetm.wasm.R
import com.mrdartsidetm.wasm.ui.instagram.InstagramViewModel

/**
 * Material 3 Expressive Home Screen for Wasm.
 * Provides a high-level overview of saved archives, quick stats for WhatsApp and Instagram,
 * and direct actions to browse messages or import new chat files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    whatsappViewModel: ChatViewModel,
    instagramViewModel: InstagramViewModel,
    onNavigateToMessages: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenInstagram: () -> Unit,
    onImportWhatsApp: () -> Unit,
    onImportInstagram: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waConversations by whatsappViewModel.conversations.collectAsStateWithLifecycle()
    val waTotalMessages by whatsappViewModel.totalMessageCount.collectAsStateWithLifecycle()

    val igAccount by instagramViewModel.account.collectAsStateWithLifecycle()
    val igConversations by instagramViewModel.conversations.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Wasm",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Universal Chat Archive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMessages) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Go to Messages"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Welcome Card
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            text = "OFFLINE ARCHIVE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Your Universal Chat Archive",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "View, search, and preserve exported chats from WhatsApp and Instagram completely offline with authentic speech bubbles, media playback, and high performance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FilledTonalButton(
                        onClick = onNavigateToMessages,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Messages", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Section Heading: Platforms
            Text(
                text = "Platforms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // WhatsApp Overview Card
            HomePlatformCard(
                platformName = "WhatsApp",
                iconResId = R.drawable.ic_nav_whatsapp,
                accentColor = Color(0xFF25D366),
                statSummary = if (waConversations.isNotEmpty()) {
                    "${waConversations.size} individual ${if (waConversations.size == 1) "chat" else "chats"} saved • $waTotalMessages messages"
                } else {
                    "No chats imported yet"
                },
                description = "Support for .txt transcripts, .zip media archives, voice notes, and contact identity switching.",
                hasData = waConversations.isNotEmpty(),
                onOpen = onOpenWhatsApp,
                onImport = onImportWhatsApp
            )

            // Instagram Overview Card
            HomePlatformCard(
                platformName = "Instagram",
                iconResId = R.drawable.ic_nav_instagram,
                accentColor = Color(0xFFE1306C),
                statSummary = if (igAccount != null) {
                    val accName = igAccount!!.displayName.ifEmpty { igAccount!!.accountName }
                    "$accName • ${igConversations.size} conversations"
                } else {
                    "No export archive imported yet"
                },
                description = "Direct message threads, media collages, voice note player, and gallery export support.",
                hasData = igAccount != null,
                onOpen = onOpenInstagram,
                onImport = onImportInstagram
            )

            // Security & Privacy Assurance Notice
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "100% Offline & Private",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Zero network requests. All chat messages and media files are stored strictly on your local device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Reusable cohesive platform summary card for the Home screen.
 */
@Composable
private fun HomePlatformCard(
    platformName: String,
    iconResId: Int,
    accentColor: Color,
    statSummary: String,
    description: String,
    hasData: Boolean,
    onOpen: () -> Unit,
    onImport: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = "$platformName Icon",
                            tint = accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasData) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (hasData) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasData) {
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open $platformName", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onImport,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import More")
                    }
                } else {
                    Button(
                        onClick = onImport,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import $platformName Chat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
