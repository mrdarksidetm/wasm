package com.mrdartsidetm.wasm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Inserts all parsed messages at once for performance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    // Returns a Flow so the UI updates automatically when the database changes
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    // Deletes old chats before importing a new one
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
