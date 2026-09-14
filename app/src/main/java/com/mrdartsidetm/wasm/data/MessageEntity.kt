package com.mrdartsidetm.wasm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MessageEntity represents a single chat bubble in the database.
 * We store the raw sender name to compare against the 'User Identity' later.
 * Linked to WhatsAppConversationEntity via conversationId.
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["conversationId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversationId: String = "default",
    val timestamp: String, // Format: "12/05/23, 14:45"
    val sender: String,    // The name as it appears in the text file
    val content: String,   // The actual message text
    val isSystemMessage: Boolean = false, // For messages like "Encryption" or "Missed Call"
    val mediaName: String? = null // Filename of any attached media
)
