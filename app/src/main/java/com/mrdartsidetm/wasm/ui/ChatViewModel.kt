package com.mrdartsidetm.wasm.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrdartsidetm.wasm.data.ChatDao
import com.mrdartsidetm.wasm.data.MessageEntity
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.data.WhatsAppConversationEntity
import com.mrdartsidetm.wasm.util.WhatsAppParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

sealed interface ImportUiState {
    object Idle : ImportUiState
    data class Loading(val step: String) : ImportUiState
    data class Success(val count: Int) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatDao: ChatDao,
    private val prefs: UserPreferencesRepository,
    val mediaDir: File
) : ViewModel() {

    // Persistent Conversations List (all individual WhatsApp chats saved)
    val conversations = chatDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Search query for conversations list
    val conversationSearchQuery = MutableStateFlow("")

    val filteredConversations: StateFlow<List<WhatsAppConversationEntity>> = combine(
        conversations,
        conversationSearchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Currently selected conversation ID (null if viewing conversation list)
    val selectedConversationId = MutableStateFlow<String?>(null)

    // Active conversation entity
    val activeConversation: StateFlow<WhatsAppConversationEntity?> = selectedConversationId
        .flatMapLatest { id ->
            if (id != null) chatDao.getConversation(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Messages for the currently active conversation
    val activeMessages: StateFlow<List<MessageEntity>> = selectedConversationId
        .flatMapLatest { id ->
            if (id != null) {
                chatDao.getMessagesForConversation(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Search query and filtered messages for in-chat search
    val searchQuery = MutableStateFlow("")
    val filteredMessages: StateFlow<List<MessageEntity>> = combine(
        activeMessages,
        searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.content.lowercase().contains(q) || it.sender.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Backward-compatibility alias for active messages
    val messages: StateFlow<List<MessageEntity>> = activeMessages

    // User identity preference
    val currentUser = prefs.userIdentity.stateIn(viewModelScope, SharingStarted.Lazily, "")

    // Derived state: Get list of unique sender names for the active conversation
    val uniqueSenders: StateFlow<List<String>> = activeMessages.map { list ->
        list.map { it.sender }
            .distinct()
            .filter { it.isNotBlank() && !it.equals("System", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Total counts across all WhatsApp chats
    val totalConversationCount = chatDao.getTotalConversationCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val totalMessageCount = chatDao.getTotalMessageCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Import operation state
    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    fun setConversationSearchQuery(query: String) {
        conversationSearchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectConversation(id: String) {
        selectedConversationId.value = id
        searchQuery.value = ""
    }

    fun closeConversation() {
        selectedConversationId.value = null
        searchQuery.value = ""
    }

    fun dismissImportState() {
        _importUiState.value = ImportUiState.Idle
    }

    /**
     * Resolves the media folder for a conversation.
     */
    fun getMediaDirForConversation(conversation: WhatsAppConversationEntity?): File {
        return if (!conversation?.mediaDirName.isNullOrBlank()) {
            File(mediaDir, conversation!!.mediaDirName)
        } else {
            mediaDir
        }
    }

    /**
     * Extracts a display title from a WhatsApp export file name or messages.
     */
    private fun extractChatTitle(fileName: String?, messages: List<MessageEntity>): String {
        if (!fileName.isNullOrBlank()) {
            var name = fileName
                .replace(".txt", "", ignoreCase = true)
                .replace(".zip", "", ignoreCase = true)
                .trim()

            val prefixes = listOf("WhatsApp Chat with ", "WhatsApp Chat - ", "WhatsApp Chat ", "WhatsApp - ")
            for (prefix in prefixes) {
                if (name.startsWith(prefix, ignoreCase = true)) {
                    name = name.substring(prefix.length).trim()
                    break
                }
            }

            if (name.isNotBlank() && !name.equals("chat", ignoreCase = true) && !name.equals("export", ignoreCase = true)) {
                return name
            }
        }

        // Fallback: examine non-system sender names
        val senders = messages.map { it.sender }.filter { it.isNotBlank() && !it.equals("System", ignoreCase = true) }.distinct()
        if (senders.isNotEmpty()) {
            return if (senders.size == 1) senders.first() else senders.joinToString(", ").take(30)
        }

        return "WhatsApp Chat"
    }

    /**
     * Imports chat from an Android content URI (.txt export) without erasing previous chats.
     * Saves each chat individually just like Instagram.
     */
    fun importChatUri(contentResolver: ContentResolver, uri: Uri, fileName: String? = null) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Reading chat file...")
            withContext(Dispatchers.IO) {
                try {
                    val text = contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not read file from storage.")

                    _importUiState.value = ImportUiState.Loading("Parsing messages...")
                    val lines = text.lines()
                    val rawEntities = WhatsAppParser.parse(lines)
                    if (rawEntities.isEmpty()) {
                        throw IllegalStateException("No valid WhatsApp messages found in this file.")
                    }

                    val title = extractChatTitle(fileName, rawEntities)
                    val safeSlug = title.filter { it.isLetterOrDigit() }.take(16).lowercase()
                    val conversationId = "wa_${System.currentTimeMillis()}_${safeSlug.ifEmpty { "chat" }}"

                    val entities = rawEntities.map { it.copy(conversationId = conversationId) }

                    val lastMsg = entities.lastOrNull { it.content.isNotBlank() }?.content?.take(100) ?: ""
                    val lastTs = entities.lastOrNull()?.timestamp ?: ""

                    val conversation = WhatsAppConversationEntity(
                        id = conversationId,
                        title = title,
                        lastMessage = lastMsg,
                        lastTimestamp = lastTs,
                        messageCount = entities.size,
                        mediaDirName = ""
                    )

                    _importUiState.value = ImportUiState.Loading("Saving chat to database...")
                    chatDao.insertMessages(entities)
                    chatDao.insertConversation(conversation)

                    withContext(Dispatchers.Main) {
                        selectedConversationId.value = conversationId
                    }

                    _importUiState.value = ImportUiState.Success(entities.size)
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to import chat file.")
                }
            }
        }
    }

    /**
     * Imports a chat from a ZIP archive URI containing the text export and attached media.
     * Creates an isolated media directory for this conversation so individual chats never collide.
     */
    fun importZipUri(contentResolver: ContentResolver, uri: Uri, fileName: String? = null) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Extracting archive & media...")
            withContext(Dispatchers.IO) {
                try {
                    if (!mediaDir.exists()) {
                        mediaDir.mkdirs()
                    }

                    var chatText = ""
                    val tempEntries = mutableListOf<Pair<String, ByteArray>>()

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val zipStream = ZipInputStream(inputStream)
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = entry.name
                                val fName = File(name).name

                                if (fName.endsWith(".txt", ignoreCase = true) && !name.contains("__MACOSX")) {
                                    val byteStream = ByteArrayOutputStream()
                                    val buffer = ByteArray(4096)
                                    var len = zipStream.read(buffer)
                                    while (len > 0) {
                                        byteStream.write(buffer, 0, len)
                                        len = zipStream.read(buffer)
                                    }
                                    chatText = byteStream.toString("UTF-8")
                                } else if (!fName.endsWith(".vcf", ignoreCase = true) && fName.isNotBlank() && !name.contains("__MACOSX")) {
                                    val byteStream = ByteArrayOutputStream()
                                    val buffer = ByteArray(4096)
                                    var len = zipStream.read(buffer)
                                    while (len > 0) {
                                        byteStream.write(buffer, 0, len)
                                        len = zipStream.read(buffer)
                                    }
                                    tempEntries.add(Pair(fName, byteStream.toByteArray()))
                                }
                            }
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                        }
                    } ?: throw IllegalStateException("Could not open ZIP stream from storage.")

                    if (chatText.isNotEmpty()) {
                        _importUiState.value = ImportUiState.Loading("Parsing messages...")
                        val lines = chatText.lines()
                        val rawEntities = WhatsAppParser.parse(lines)
                        if (rawEntities.isEmpty()) {
                            throw IllegalStateException("No valid WhatsApp messages found in export.")
                        }

                        val title = extractChatTitle(fileName, rawEntities)
                        val safeSlug = title.filter { it.isLetterOrDigit() }.take(16).lowercase()
                        val conversationId = "wa_${System.currentTimeMillis()}_${safeSlug.ifEmpty { "chat" }}"

                        // Write media to conversation-specific directory
                        val chatMediaDir = File(mediaDir, conversationId)
                        chatMediaDir.mkdirs()
                        val canonicalDirPath = chatMediaDir.canonicalPath + File.separator

                        for ((mediaFileName, bytes) in tempEntries) {
                            val destFile = File(chatMediaDir, mediaFileName)
                            if (destFile.canonicalPath.startsWith(canonicalDirPath)) {
                                destFile.writeBytes(bytes)
                            }
                        }

                        val entities = rawEntities.map { it.copy(conversationId = conversationId) }
                        val lastMsg = entities.lastOrNull { it.content.isNotBlank() }?.content?.take(100) ?: ""
                        val lastTs = entities.lastOrNull()?.timestamp ?: ""

                        val conversation = WhatsAppConversationEntity(
                            id = conversationId,
                            title = title,
                            lastMessage = lastMsg,
                            lastTimestamp = lastTs,
                            messageCount = entities.size,
                            mediaDirName = conversationId
                        )

                        _importUiState.value = ImportUiState.Loading("Saving chat to database...")
                        chatDao.insertMessages(entities)
                        chatDao.insertConversation(conversation)

                        withContext(Dispatchers.Main) {
                            selectedConversationId.value = conversationId
                        }

                        _importUiState.value = ImportUiState.Success(entities.size)
                    } else {
                        _importUiState.value = ImportUiState.Error("No .txt chat export found inside this ZIP archive.")
                    }
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to extract ZIP archive.")
                }
            }
        }
    }

    /**
     * Backward compatibility helper for raw text string.
     */
    fun importChatFile(content: String) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Parsing chat text...")
            withContext(Dispatchers.IO) {
                try {
                    val lines = content.lines()
                    val rawEntities = WhatsAppParser.parse(lines)
                    val title = extractChatTitle(null, rawEntities)
                    val conversationId = "wa_${System.currentTimeMillis()}"
                    val entities = rawEntities.map { it.copy(conversationId = conversationId) }
                    val lastMsg = entities.lastOrNull { it.content.isNotBlank() }?.content?.take(100) ?: ""
                    val lastTs = entities.lastOrNull()?.timestamp ?: ""
                    val conversation = WhatsAppConversationEntity(
                        id = conversationId,
                        title = title,
                        lastMessage = lastMsg,
                        lastTimestamp = lastTs,
                        messageCount = entities.size,
                        mediaDirName = ""
                    )
                    chatDao.insertMessages(entities)
                    chatDao.insertConversation(conversation)
                    withContext(Dispatchers.Main) {
                        selectedConversationId.value = conversationId
                    }
                    _importUiState.value = ImportUiState.Success(entities.size)
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to import chat.")
                }
            }
        }
    }

    /**
     * Deletes a single WhatsApp conversation, its messages, and its attached media files.
     */
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val conv = chatDao.getConversation(id).firstOrNull()
                chatDao.deleteMessagesForConversation(id)
                chatDao.deleteConversation(id)
                if (conv != null && conv.mediaDirName.isNotBlank()) {
                    val convMediaDir = File(mediaDir, conv.mediaDirName)
                    if (convMediaDir.exists()) {
                        convMediaDir.deleteRecursively()
                    }
                }
            }
            if (selectedConversationId.value == id) {
                selectedConversationId.value = null
            }
        }
    }

    /**
     * Clears all imported WhatsApp messages, conversations, and media files.
     */
    fun clearAllWhatsApp() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatDao.clearAll()
                if (mediaDir.exists()) {
                    mediaDir.deleteRecursively()
                }
            }
            searchQuery.value = ""
            conversationSearchQuery.value = ""
            selectedConversationId.value = null
            _importUiState.value = ImportUiState.Idle
        }
    }

    /**
     * Backward compatibility clear function.
     */
    fun clearChat() {
        val currentId = selectedConversationId.value
        if (currentId != null) {
            deleteConversation(currentId)
        } else {
            clearAllWhatsApp()
        }
    }

    fun setIdentity(name: String) {
        viewModelScope.launch {
            prefs.saveUserIdentity(name)
        }
    }
}
