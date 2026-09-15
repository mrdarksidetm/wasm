package com.mrdartsidetm.wasm.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.ui.instagram.InstagramViewModel
import java.io.File
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Material 3 Expressive Home Screen for Wasm.
 * Provides a pristine, clean canvas layout:
 * - Status bar space consideration.
 * - Dynamic time-based heading ("Good Morning" / "Good Afternoon" / "Good Evening").
 * - Horizontal horizon profile avatar icon opening the Personalize page with a top-to-bottom transition.
 * - Frosted glass rectangle animated card with smooth 0-to-total message count animation
 *   and subtle primary/tertiary ambient background colors.
 */
@Composable
fun HomeScreen(
    whatsappViewModel: ChatViewModel,
    instagramViewModel: InstagramViewModel,
    prefs: UserPreferencesRepository,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenWhatsApp: () -> Unit = {},
    onOpenInstagram: () -> Unit = {},
    onImportWhatsApp: () -> Unit = {},
    onImportInstagram: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Collect messages and archive stats
    val waTotalMessages by whatsappViewModel.totalMessageCount.collectAsStateWithLifecycle(initialValue = 0)
    val igConversations by instagramViewModel.conversations.collectAsStateWithLifecycle()
    val igTotalMessages: Int = remember(igConversations) { igConversations.sumOf { it.messageCount } }
    val totalImportedMessages: Int = waTotalMessages + igTotalMessages

    // Personalized user profile preferences
    val savedPhotoPath by prefs.profileImagePath.collectAsStateWithLifecycle(initialValue = null)
    val savedPersonName by prefs.personName.collectAsStateWithLifecycle(initialValue = "")

    val profileBitmap = remember(savedPhotoPath) {
        savedPhotoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    // Dynamic greeting based on current system time
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val timeGreeting = remember(currentHour) {
        when (currentHour) {
            in 4..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Controls top-to-bottom Personalize screen overlay
    var isPersonalizeOpen by rememberSaveable { mutableStateOf(false) }

    // Counter animation from 0 to totalImportedMessages
    var targetCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(totalImportedMessages) {
        targetCount = totalImportedMessages
    }

    val animatedCount by animateIntAsState(
        targetValue = targetCount,
        animationSpec = tween(
            durationMillis = 1400,
            easing = FastOutSlowInEasing
        ),
        label = "totalMessagesCounterAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Home Screen Canvas
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Horizon Row: "Good X" heading on left, Profile avatar on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = timeGreeting,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (savedPersonName.isNotBlank()) {
                        Text(
                            text = savedPersonName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Profile Avatar Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isPersonalizeOpen = true }
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap,
                                contentDescription = "Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Open Personalize",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Rectangle Animated Card with Frosted Glass Effect & Primary/Tertiary Ambient Background
            val frostedGlassBrush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                )
            )

            val frostedBorderBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.40f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                )
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, frostedBorderBrush),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(frostedGlassBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Card Header with Activity Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Imported Archives",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Animated 0 -> Total Messages Count Display
                    val formattedNumber = remember(animatedCount) {
                        NumberFormat.getNumberInstance(Locale.getDefault()).format(animatedCount)
                    }

                    Text(
                        text = formattedNumber,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Total Messages Imported",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (totalImportedMessages > 0) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (waTotalMessages > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "WhatsApp: ${NumberFormat.getNumberInstance().format(waTotalMessages)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (igTotalMessages > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "Instagram: ${NumberFormat.getNumberInstance().format(igTotalMessages)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Remaining space is a pristine blank canvas as instructed
            Spacer(modifier = Modifier.weight(1f))
        }

        // Top-to-Bottom Animated Personalize Page Overlay
        AnimatedVisibility(
            visible = isPersonalizeOpen,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        ) {
            PersonalizeScreen(
                prefs = prefs,
                onDismiss = { isPersonalizeOpen = false },
                onNavigateToSettings = {
                    isPersonalizeOpen = false
                    onNavigateToSettings()
                }
            )
        }
    }
}
