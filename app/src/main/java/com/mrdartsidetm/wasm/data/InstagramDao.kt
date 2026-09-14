package com.mrdartsidetm.wasm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstagramDao {

    // Account operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: InstagramAccountEntity)

    @Query("SELECT * FROM instagram_account WHERE id = 1 LIMIT 1")
    fun getAccount(): Flow<InstagramAccountEntity?>

    @Query("UPDATE instagram_account SET isDived = :isDived WHERE id = 1")
    suspend fun setDived(isDived: Boolean)

    // Conversation operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<InstagramConversationEntity>)

    @Query("SELECT * FROM instagram_conversations ORDER BY lastTimestamp DESC, title ASC")
    fun getAllConversations(): Flow<List<InstagramConversationEntity>>

    @Query("SELECT * FROM instagram_conversations WHERE id = :conversationId LIMIT 1")
    fun getConversation(conversationId: String): Flow<InstagramConversationEntity?>

    @Query("SELECT * FROM instagram_conversations WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchConversations(query: String): Flow<List<InstagramConversationEntity>>

    // Message operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<InstagramMessageEntity>)

    @Query("SELECT * FROM instagram_messages WHERE conversationId = :conversationId ORDER BY epochTime ASC, id ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<InstagramMessageEntity>>

    @Query("SELECT * FROM instagram_messages WHERE conversationId = :conversationId AND (mediaType IS NOT NULL OR mediaPath IS NOT NULL OR mediaPaths IS NOT NULL) ORDER BY epochTime DESC, id DESC")
    fun getMediaMessagesForConversation(conversationId: String): Flow<List<InstagramMessageEntity>>

    @Query("SELECT * FROM instagram_messages WHERE conversationId = :conversationId AND content LIKE '%' || :query || '%' ORDER BY epochTime ASC")
    fun searchMessagesInConversation(conversationId: String, query: String): Flow<List<InstagramMessageEntity>>

    @Query("SELECT COUNT(*) FROM instagram_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    // Clean up
    @Query("DELETE FROM instagram_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM instagram_conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM instagram_account")
    suspend fun clearAccount()

    suspend fun clearAll() {
        clearMessages()
        clearConversations()
        clearAccount()
    }
}
