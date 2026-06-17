package com.mrdartsidetm.wasm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MessageEntity represents a single chat bubble in the database.
 * We store the raw sender name to compare against the 'User Identity' later.
 */
@Entity(tableName = "chat_messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String, // Format: "12/05/23, 14:45"
    val sender: String,    // The name as it appears in the text file
    val content: String,   // The actual message text
    val isSystemMessage: Boolean = false, // For messages like "Encryption" or "Missed Call"
    val mediaName: String? = null // Filename of any attached media
)
