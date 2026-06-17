package com.mrdartsidetm.wasm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrdartsidetm.wasm.data.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onImportClick: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val senders by viewModel.uniqueSenders.collectAsState()

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
                ChatBubble(message, isMe)
            }
        }
    }
}

@Composable
fun ChatBubble(message: MessageEntity, isMe: Boolean) {
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
                Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
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
