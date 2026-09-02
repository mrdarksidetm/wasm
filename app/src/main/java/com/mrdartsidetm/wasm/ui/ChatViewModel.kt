package com.mrdartsidetm.wasm.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrdartsidetm.wasm.data.ChatDao
import com.mrdartsidetm.wasm.data.MessageEntity
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.util.WhatsAppParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface ImportUiState {
    object Idle : ImportUiState
    data class Loading(val step: String) : ImportUiState
    data class Success(val count: Int) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class ChatViewModel(
    private val chatDao: ChatDao,
    private val prefs: UserPreferencesRepository,
    val mediaDir: File
) : ViewModel() {

    // UI State: Holds all parsed messages
    val messages = chatDao.getAllMessages().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val currentUser = prefs.userIdentity.stateIn(viewModelScope, SharingStarted.Lazily, "")

    // Search query and filtered messages for real-time search
    val searchQuery = MutableStateFlow("")
    val filteredMessages: StateFlow<List<MessageEntity>> = combine(messages, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.content.lowercase().contains(q) || it.sender.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Derived state: Get list of unique sender names for the "Who are you?" dialog
    val uniqueSenders = messages.map { list ->
        list.map { it.sender }
            .distinct()
            .filter { it.isNotBlank() && !it.equals("System", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Import operation state for Material 3 progress indicators and alerts
    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun dismissImportState() {
        _importUiState.value = ImportUiState.Idle
    }

    /**
     * Imports chat from an Android content URI safely keeping the stream open on Dispatchers.IO.
     */
    fun importChatUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Reading chat file...")
            withContext(Dispatchers.IO) {
                try {
                    val text = contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not read file from storage.")

                    _importUiState.value = ImportUiState.Loading("Parsing messages...")
                    val lines = text.lines()
                    val entities = WhatsAppParser.parse(lines)

                    _importUiState.value = ImportUiState.Loading("Saving messages to local database...")
                    chatDao.clearAll()
                    chatDao.insertMessages(entities)

                    _importUiState.value = ImportUiState.Success(entities.size)
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to import chat file.")
                }
            }
        }
    }

    /**
     * Imports a chat from a ZIP archive URI containing the text export and attached media.
     * Keeps the stream alive within the IO coroutine, preventing stream closed crashes.
     */
    fun importZipUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Extracting archive & media...")
            withContext(Dispatchers.IO) {
                try {
                    // Re-create media directory cleanly
                    if (mediaDir.exists()) {
                        mediaDir.deleteRecursively()
                    }
                    mediaDir.mkdirs()

                    var chatText = ""
                    val canonicalMediaDirPath = mediaDir.canonicalPath + File.separator

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val zipStream = ZipInputStream(inputStream)
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = entry.name
                                val fileName = File(name).name

                                if (fileName.endsWith(".txt", ignoreCase = true) && !name.contains("__MACOSX")) {
                                    val byteStream = ByteArrayOutputStream()
                                    val buffer = ByteArray(4096)
                                    var len = zipStream.read(buffer)
                                    while (len > 0) {
                                        byteStream.write(buffer, 0, len)
                                        len = zipStream.read(buffer)
                                    }
                                    chatText = byteStream.toString("UTF-8")
                                } else if (!fileName.endsWith(".vcf", ignoreCase = true) && fileName.isNotBlank()) {
                                    // Zip Slip path traversal vulnerability protection
                                    val destFile = File(mediaDir, fileName)
                                    if (destFile.canonicalPath.startsWith(canonicalMediaDirPath)) {
                                        destFile.outputStream().use { output ->
                                            zipStream.copyTo(output)
                                        }
                                    }
                                }
                            }
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                        }
                    } ?: throw IllegalStateException("Could not open ZIP stream from storage.")

                    if (chatText.isNotEmpty()) {
                        _importUiState.value = ImportUiState.Loading("Parsing messages...")
                        val lines = chatText.lines()
                        val entities = WhatsAppParser.parse(lines)

                        _importUiState.value = ImportUiState.Loading("Saving messages to local database...")
                        chatDao.clearAll()
                        chatDao.insertMessages(entities)

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
     * Processes raw text directly (backward compatibility helper)
     */
    fun importChatFile(content: String) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading("Parsing chat text...")
            withContext(Dispatchers.IO) {
                try {
                    val lines = content.lines()
                    val entities = WhatsAppParser.parse(lines)
                    chatDao.clearAll()
                    chatDao.insertMessages(entities)
                    _importUiState.value = ImportUiState.Success(entities.size)
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error(e.message ?: "Failed to import chat.")
                }
            }
        }
    }

    /**
     * Clears all imported messages, media files, and resets search.
     */
    fun clearChat() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatDao.clearAll()
                if (mediaDir.exists()) {
                    mediaDir.deleteRecursively()
                }
            }
            searchQuery.value = ""
            _importUiState.value = ImportUiState.Idle
        }
    }

    fun setIdentity(name: String) {
        viewModelScope.launch {
            prefs.saveUserIdentity(name)
        }
    }
}
