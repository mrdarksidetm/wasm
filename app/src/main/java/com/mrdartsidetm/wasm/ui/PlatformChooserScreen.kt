package com.mrdartsidetm.wasm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrdartsidetm.wasm.R

/**
 * PlatformChooserScreen allows users to choose between WhatsApp and Instagram chat archives.
 * Rigorously styled with Material 3 Expressive design:
 * - Centered Globe icon (no emoji)
 * - "Choose the Platform" heading
 * - Two big cohesive buttons with custom icons for WhatsApp and Instagram
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformChooserScreen(
    whatsappChatCount: Int,
    instagramConversationCount: Int,
    onSelectWhatsApp: () -> Unit,
    onSelectInstagram: () -> Unit,
    onBackToHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBackToHome != null) {
                        IconButton(onClick = onBackToHome) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Center Globe Icon (No Emoji)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Platform Selector Globe",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Heading: Choose the Platform
            Text(
                text = "Choose the Platform",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select a messaging platform to browse and manage your saved chat archives.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Big WhatsApp Button
            PlatformChoiceCard(
                title = "WhatsApp",
                subtitle = if (whatsappChatCount > 0) {
                    "$whatsappChatCount individual ${if (whatsappChatCount == 1) "chat" else "chats"} saved"
                } else {
                    "Exported .txt & .zip chat archives"
                },
                iconResId = R.drawable.ic_nav_whatsapp,
                accentColor = Color(0xFF25D366),
                badgeText = if (whatsappChatCount > 0) "$whatsappChatCount" else null,
                onClick = onSelectWhatsApp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Big Instagram Button
            PlatformChoiceCard(
                title = "Instagram",
                subtitle = if (instagramConversationCount > 0) {
                    "$instagramConversationCount ${if (instagramConversationCount == 1) "conversation" else "conversations"} saved"
                } else {
                    "Direct messages, media & voice notes"
                },
                iconResId = R.drawable.ic_nav_instagram,
                accentColor = Color(0xFFE1306C),
                badgeText = if (instagramConversationCount > 0) "$instagramConversationCount" else null,
                onClick = onSelectInstagram
            )
        }
        }
    }
}

/**
 * Cohesive Material 3 Expressive Card button for platform selection.
 */
@Composable
private fun PlatformChoiceCard(
    title: String,
    subtitle: String,
    iconResId: Int,
    accentColor: Color,
    badgeText: String?,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Brand Icon Container
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = "$title Icon",
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Subtitle Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    badgeText?.let { count ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = count,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Chevron
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
