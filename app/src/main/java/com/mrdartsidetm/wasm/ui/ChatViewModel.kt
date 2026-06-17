package com.mrdartsidetm.wasm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrdartsidetm.wasm.data.ChatDao
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.util.WhatsAppParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatDao: ChatDao,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    // UI State: Holds messages, unique senders, and the current user
    val messages = chatDao.getAllMessages().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val currentUser = prefs.userIdentity.stateIn(viewModelScope, SharingStarted.Lazily, "")

    // Derived state: Get list of unique names for the "Who are you?" dialog
    val uniqueSenders = messages.map { list ->
        list.map { it.sender }.distinct().filter { it.isNotBlank() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Processes the raw text from the file picker
     */
    fun importChatFile(content: String) {
        viewModelScope.launch {
            val lines = content.lines()
            val entities = WhatsAppParser.parse(lines)
            chatDao.clearAll() // Fresh import
            chatDao.insertMessages(entities)
        }
    }

    fun setIdentity(name: String) {
        viewModelScope.launch {
            prefs.saveUserIdentity(name)
        }
    }
}
