package com.mrdartsidetm.wasm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the Instagram account profile detected from export metadata (start_here.html / chats.html).
 */
@Entity(tableName = "instagram_account")
data class InstagramAccountEntity(
    @PrimaryKey val id: Int = 1,
    val accountName: String, // e.g. "kajal.23.sinha"
    val displayName: String, // e.g. "Kajal Sinha"
    val exportDate: String,  // e.g. "Monday, September 14, 2026 at 4:28 AM UTC"
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val isDived: Boolean = false // Tracks if user clicked "Let's Dive" on landing page
)

/**
 * Represents a single conversation thread with another user or group.
 */
@Entity(tableName = "instagram_conversations")
data class InstagramConversationEntity(
    @PrimaryKey val id: String, // e.g. "abhijeetyadav_1424166558982770" or "1405380277523017"
    val title: String,          // e.g. "Abhijeet Yadav", "Aryan", "❤"
    val isRequest: Boolean = false, // True if located in message_requests
    val lastMessage: String = "",
    val lastTimestamp: String = "",
    val messageCount: Int = 0,
    val folderPath: String = "" // Relative path e.g. "inbox/abhijeetyadav_1424166558982770"
)

/**
 * Represents a single message within an Instagram conversation thread.
 */
@Entity(
    tableName = "instagram_messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["epochTime"])
    ]
)
data class InstagramMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String, // Foreign key linking to InstagramConversationEntity.id
    val sender: String,         // Display name of sender (e.g. "Kajal Sinha" or "Abhijeet Yadav")
    val timestamp: String,      // Human-readable timestamp e.g. "Aug 16, 2024 10:58 am"
    val epochTime: Long = 0L,   // Milliseconds since epoch for precise chronological ordering
    val content: String = "",   // Text message body or caption
    val mediaType: String? = null, // "photo", "video", "audio", "gif", "reel", "link", "album"
    val mediaPath: String? = null, // Primary / first relative path e.g. "inbox/.../photos/123.jpg"
    val mediaPaths: String? = null, // Pipe-separated list: "photo:path1|photo:path2|video:path3"
    val reactions: String? = null, // Emoji reaction string e.g. "❤️ Kajal Sinha"
    val sharedUrl: String? = null, // Shared Instagram Reel, post, or web link
    val sharedTitle: String? = null, // Shared post caption / title
    val isOutgoing: Boolean = false  // True if sent by the account owner
) {
    /**
     * Returns all media items in this message as a list of (mediaType, relativePath).
     */
    fun getMediaList(): List<Pair<String, String>> {
        if (!mediaPaths.isNullOrBlank()) {
            return mediaPaths.split('|').mapNotNull { entry ->
                val colonIdx = entry.indexOf(':')
                if (colonIdx != -1) {
                    Pair(entry.substring(0, colonIdx), entry.substring(colonIdx + 1))
                } else if (!mediaType.isNullOrBlank()) {
                    Pair(mediaType, entry)
                } else null
            }
        }
        if (!mediaPath.isNullOrBlank() && !mediaType.isNullOrBlank()) {
            return listOf(Pair(mediaType, mediaPath))
        }
        return emptyList()
    }
}
