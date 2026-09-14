package com.mrdartsidetm.wasm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an individual WhatsApp conversation thread saved in the local database.
 * Modeled just like InstagramConversationEntity to persist each individual chat separately.
 */
@Entity(tableName = "whatsapp_conversations")
data class WhatsAppConversationEntity(
    @PrimaryKey val id: String, // e.g. "wa_1726348123_john_doe"
    val title: String,          // Contact or group display name
    val lastMessage: String = "",
    val lastTimestamp: String = "",
    val messageCount: Int = 0,
    val mediaDirName: String = "" // Relative folder name in media directory for this conversation's attachments
)
