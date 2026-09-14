package com.mrdartsidetm.wasm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // Conversation operations (Individual WhatsApp Chats)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: WhatsAppConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<WhatsAppConversationEntity>)

    @Query("SELECT * FROM whatsapp_conversations ORDER BY id DESC")
    fun getAllConversations(): Flow<List<WhatsAppConversationEntity>>

    @Query("SELECT * FROM whatsapp_conversations WHERE id = :conversationId LIMIT 1")
    fun getConversation(conversationId: String): Flow<WhatsAppConversationEntity?>

    @Query("SELECT * FROM whatsapp_conversations WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchConversations(query: String): Flow<List<WhatsAppConversationEntity>>

    @Query("DELETE FROM whatsapp_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM whatsapp_conversations")
    suspend fun clearAllConversations()

    // Message operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND (content LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%') ORDER BY id ASC")
    fun searchMessagesInConversation(conversationId: String, query: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages")
    fun getTotalMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM whatsapp_conversations")
    fun getTotalConversationCount(): Flow<Int>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    suspend fun clearAll() {
        clearAllMessages()
        clearAllConversations()
    }
}
