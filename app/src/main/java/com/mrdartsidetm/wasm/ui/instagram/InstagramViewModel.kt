package com.mrdartsidetm.wasm.ui.instagram

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrdartsidetm.wasm.data.InstagramAccountEntity
import com.mrdartsidetm.wasm.data.InstagramConversationEntity
import com.mrdartsidetm.wasm.data.InstagramDao
import com.mrdartsidetm.wasm.data.InstagramMessageEntity
import com.mrdartsidetm.wasm.ui.ImportUiState
import com.mrdartsidetm.wasm.util.AudioPlayerManager
import com.mrdartsidetm.wasm.util.InstagramHtmlParser
import com.mrdartsidetm.wasm.util.InstagramZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class InstagramConversationFilter {
    ALL,
    INBOX,
    REQUESTS
}

class InstagramViewModel(
    private val instagramDao: InstagramDao,
    val instagramBaseDir: File
) : ViewModel() {

    val audioPlayerManager = AudioPlayerManager(viewModelScope)

    val account = instagramDao.getAccount().stateIn(viewModelScope, SharingStarted.Lazily, null)

    val conversations = instagramDao.getAllConversations().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeFilter = MutableStateFlow(InstagramConversationFilter.ALL)
    val searchQuery = MutableStateFlow("")

    val filteredConversations: StateFlow<List<InstagramConversationEntity>> = combine(
        conversations,
        activeFilter,
        searchQuery
    ) { list, filter, query ->
        var result = when (filter) {
            InstagramConversationFilter.ALL -> list
            InstagramConversationFilter.INBOX -> list.filter { !it.isRequest }
            InstagramConversationFilter.REQUESTS -> list.filter { it.isRequest }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q) }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedConversationId = MutableStateFlow<String?>(null)

    val currentConversation: StateFlow<InstagramConversationEntity?> = selectedConversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            instagramDao.getConversation(id)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val conversationSearchQuery = MutableStateFlow("")

    val currentMessages: StateFlow<List<InstagramMessageEntity>> = combine(
        selectedConversationId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                instagramDao.getMessagesForConversation(id)
            }
        },
        conversationSearchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.content.lowercase().contains(q) || it.sender.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    fun dismissImportState() {
        _importUiState.value = ImportUiState.Idle
    }

    fun setFilter(filter: InstagramConversationFilter) {
        activeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setConversationSearchQuery(query: String) {
        conversationSearchQuery.value = query
    }

    // Conversation Details page state
    val isViewingConversationDetails = MutableStateFlow(false)

    fun openConversationDetails() {
        isViewingConversationDetails.value = true
    }

    fun closeConversationDetails() {
        isViewingConversationDetails.value = false
    }

    // Media messages for conversation details
    val conversationMediaMessages: StateFlow<List<InstagramMessageEntity>> = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else instagramDao.getMediaMessagesForConversation(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All media files in the conversation for the 3-column gallery
    val allConversationMediaFiles: StateFlow<List<Pair<String, File>>> = conversationMediaMessages.map { list ->
        val result = mutableListOf<Pair<String, File>>()
        for (msg in list) {
            val items = msg.getMediaList()
            for ((type, path) in items) {
                if (type == "photo" || type == "video" || type == "gif") {
                    val file = resolveMediaFile(path, msg.conversationId)
                    if (file != null && file.exists()) {
                        result.add(Pair(type, file))
                    }
                }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All voice notes in the conversation
    val allConversationVoiceNotes: StateFlow<List<InstagramMessageEntity>> = currentMessages.map { list ->
        list.filter { it.mediaType == "audio" || it.getMediaList().any { m -> m.first == "audio" } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All shared links and reels in the conversation
    val allConversationLinks: StateFlow<List<InstagramMessageEntity>> = currentMessages.map { list ->
        list.filter { it.sharedUrl != null }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Expanded Media Viewer Page state (vertical scrollable view with hold-to-select and 3-dots download)
    val activeExpandedMediaList = MutableStateFlow<List<Pair<String, File>>?>(null)
    val activeMediaViewerTitle = MutableStateFlow("Media")

    fun openExpandedMedia(mediaList: List<Pair<String, File>>, title: String = "Media") {
        activeExpandedMediaList.value = mediaList
        activeMediaViewerTitle.value = title
    }

    fun closeExpandedMedia() {
        activeExpandedMediaList.value = null
    }

    /**
     * Resolves all media files attached to a message.
     */
    fun resolveMessageMedia(message: InstagramMessageEntity): List<Pair<String, File>> {
        val items = message.getMediaList()
        val result = mutableListOf<Pair<String, File>>()
        for ((type, path) in items) {
            val file = resolveMediaFile(path, message.conversationId)
            if (file != null && file.exists()) {
                result.add(Pair(type, file))
            }
        }
        return result
    }

    fun selectConversation(id: String) {
        selectedConversationId.value = id
        conversationSearchQuery.value = ""
        isViewingConversationDetails.value = false
        activeExpandedMediaList.value = null
        ensureConversationLoaded(id)
    }

    fun closeConversation() {
        audioPlayerManager.stop()
        selectedConversationId.value = null
        conversationSearchQuery.value = ""
        isViewingConversationDetails.value = false
        activeExpandedMediaList.value = null
    }

    fun diveIn() {
        viewModelScope.launch {
            instagramDao.setDived(true)
        }
    }

    fun backToLanding() {
        viewModelScope.launch {
            instagramDao.setDived(false)
        }
    }

    /**
     * Imports an Instagram export ZIP archive.
     */
    fun importZipUri(contentResolver: ContentResolver, uri: Uri, fallbackName: String? = null) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Extracting Instagram messages & media...")
            withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val extractionResult = InstagramZipExtractor.extract(
                            inputStream = stream,
                            targetDir = instagramBaseDir,
                            fallbackZipName = fallbackName,
                            onProgress = { step, _ ->
                                _importUiState.value = ImportUiState.Loading(step)
                            }
                        )

                        _importUiState.value = ImportUiState.Loading("Indexing conversations & chats...")

                        // Parse chats.html to discover all conversations
                        val chatsFile = extractionResult.chatsFile
                        val chatIndexItems = if (chatsFile != null && chatsFile.exists()) {
                            InstagramHtmlParser.parseChatsList(chatsFile.readText(Charsets.UTF_8))
                        } else {
                            emptyList()
                        }

                        // Index conversation folders from disk as well
                        val inboxDir = File(extractionResult.messagesDir, "inbox")
                        val requestsDir = File(extractionResult.messagesDir, "message_requests")

                        val conversationsList = mutableListOf<InstagramConversationEntity>()
                        var totalParsedMessagesCount = 0

                        val ownerName = extractionResult.account.displayName

                        // Build conversation list from chats.html or discovered folders
                        val allFolders = mutableSetOf<String>()
                        chatIndexItems.forEach { item ->
                            allFolders.add(item.folderId)
                        }
                        inboxDir.listFiles()?.filter { it.isDirectory }?.forEach { allFolders.add(it.name) }
                        requestsDir.listFiles()?.filter { it.isDirectory }?.forEach { allFolders.add(it.name) }

                        val totalFolders = allFolders.size
                        var processedIndex = 0

                        for (folderId in allFolders) {
                            processedIndex++
                            _importUiState.value = ImportUiState.Loading(
                                "Processing chats ($processedIndex of $totalFolders)..."
                            )

                            val matchingItem = chatIndexItems.find { it.folderId == folderId }
                            val isRequest = matchingItem?.isRequest ?: (File(requestsDir, folderId).exists())
                            val parentDir = if (isRequest) requestsDir else inboxDir
                            val chatFolder = File(parentDir, folderId)

                            val title = matchingItem?.title
                                ?: folderId.substringBefore('_').replace('.', ' ')
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                            // Check all message_*.html files in this chat folder
                            val messageFiles = chatFolder.listFiles { file ->
                                file.isFile && file.name.startsWith("message_") && file.name.endsWith(".html")
                            }?.sortedBy { it.name } ?: emptyList()

                            var lastMessagePreview = ""
                            var lastMessageTime = ""
                            val folderMessages = mutableListOf<InstagramMessageEntity>()

                            for (msgFile in messageFiles) {
                                try {
                                    val html = msgFile.readText(Charsets.UTF_8)
                                    val parsed = InstagramHtmlParser.parseConversationMessages(
                                        htmlContent = html,
                                        conversationId = folderId,
                                        accountOwnerDisplayName = ownerName
                                    )
                                    folderMessages.addAll(parsed)
                                } catch (e: Exception) {
                                    // continue with other files
                                }
                            }

                            if (folderMessages.isNotEmpty()) {
                                // Messages are chronological (oldest to newest), so last element is latest
                                val latest = folderMessages.last()
                                lastMessagePreview = when {
                                    latest.content.isNotBlank() -> latest.content
                                    latest.mediaType == "photo" -> "📷 Photo"
                                    latest.mediaType == "video" -> "🎥 Video"
                                    latest.mediaType == "audio" -> "🎤 Voice message"
                                    latest.mediaType == "gif" -> "👾 Sticker / GIF"
                                    latest.mediaType == "reel" -> "🎞 Shared Reel"
                                    else -> "Attachment"
                                }
                                lastMessageTime = latest.timestamp
                            }

                            // Insert messages in batches
                            if (folderMessages.isNotEmpty()) {
                                folderMessages.chunked(500).forEach { chunk ->
                                    instagramDao.insertMessages(chunk)
                                }
                                totalParsedMessagesCount += folderMessages.size
                            }

                            conversationsList.add(
                                InstagramConversationEntity(
                                    id = folderId,
                                    title = title,
                                    isRequest = isRequest,
                                    lastMessage = lastMessagePreview,
                                    lastTimestamp = lastMessageTime,
                                    messageCount = folderMessages.size,
                                    folderPath = if (isRequest) "message_requests/$folderId" else "inbox/$folderId"
                                )
                            )
                        }

                        // Save conversations
                        instagramDao.insertConversations(conversationsList)

                        // Save updated account profile
                        val finalAccount = extractionResult.account.copy(
                            totalConversations = conversationsList.size,
                            totalMessages = totalParsedMessagesCount,
                            isDived = false
                        )
                        instagramDao.insertAccount(finalAccount)

                        _importUiState.value = ImportUiState.Success(conversationsList.size)
                    } ?: throw IllegalStateException("Could not open selected ZIP file.")
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to import Instagram archive.")
                }
            }
        }
    }

    /**
     * Lazy verification that conversation messages are loaded into Room.
     */
    private fun ensureConversationLoaded(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val count = instagramDao.getMessageCount(conversationId)
                if (count == 0) {
                    // Search for message files in base directory
                    val messagesDir = File(instagramBaseDir, "messages").takeIf { it.exists() } ?: instagramBaseDir
                    val candidateDirs = listOf(
                        File(messagesDir, "inbox/$conversationId"),
                        File(messagesDir, "message_requests/$conversationId")
                    )
                    val chatFolder = candidateDirs.firstOrNull { it.exists() } ?: return@withContext

                    val messageFiles = chatFolder.listFiles { file ->
                        file.isFile && file.name.startsWith("message_") && file.name.endsWith(".html")
                    }?.sortedBy { it.name } ?: emptyList()

                    val ownerName = account.value?.displayName ?: "Kajal Sinha"
                    val parsedMessages = mutableListOf<InstagramMessageEntity>()
                    for (file in messageFiles) {
                        val parsed = InstagramHtmlParser.parseConversationMessages(
                            htmlContent = file.readText(Charsets.UTF_8),
                            conversationId = conversationId,
                            accountOwnerDisplayName = ownerName
                        )
                        parsedMessages.addAll(parsed)
                    }

                    if (parsedMessages.isNotEmpty()) {
                        parsedMessages.chunked(500).forEach {
                            instagramDao.insertMessages(it)
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves a media file path relative to the extracted Instagram base directory.
     */
    fun resolveMediaFile(mediaPath: String?, conversationId: String?): File? {
        if (mediaPath.isNullOrBlank()) return null
        val cleanPath = mediaPath.replace('\\', '/')

        // 1. Direct path from instagramBaseDir
        val direct = File(instagramBaseDir, cleanPath)
        if (direct.exists() && direct.isFile) return direct

        // 2. Relative from messages/
        val messagesIndex = cleanPath.indexOf("messages/")
        if (messagesIndex != -1) {
            val sub = cleanPath.substring(messagesIndex + "messages/".length)
            val fileInMessages = File(instagramBaseDir, "messages/$sub")
            if (fileInMessages.exists() && fileInMessages.isFile) return fileInMessages

            val fileAtRoot = File(instagramBaseDir, sub)
            if (fileAtRoot.exists() && fileAtRoot.isFile) return fileAtRoot
        }

        // 3. Search in conversation directory
        if (!conversationId.isNullOrBlank()) {
            val fileName = File(cleanPath).name
            val messagesDir = File(instagramBaseDir, "messages").takeIf { it.exists() } ?: instagramBaseDir
            val inboxThreadDir = File(messagesDir, "inbox/$conversationId")
            val reqThreadDir = File(messagesDir, "message_requests/$conversationId")

            for (threadDir in listOf(inboxThreadDir, reqThreadDir)) {
                if (threadDir.exists()) {
                    val subDirs = listOf("photos", "videos", "audio", "gifs")
                    for (sub in subDirs) {
                        val candidate = File(File(threadDir, sub), fileName)
                        if (candidate.exists() && candidate.isFile) return candidate
                    }
                    val candidateDirect = File(threadDir, fileName)
                    if (candidateDirect.exists() && candidateDirect.isFile) return candidateDirect
                }
            }
        }

        return null
    }

    /**
     * Clears all stored Instagram messages, conversations, and deletes extracted media files.
     */
    fun clearInstagramData() {
        viewModelScope.launch {
            audioPlayerManager.stop()
            withContext(Dispatchers.IO) {
                instagramDao.clearAll()
                if (instagramBaseDir.exists()) {
                    instagramBaseDir.deleteRecursively()
                }
            }
            selectedConversationId.value = null
            searchQuery.value = ""
            conversationSearchQuery.value = ""
            _importUiState.value = ImportUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.release()
    }
}
