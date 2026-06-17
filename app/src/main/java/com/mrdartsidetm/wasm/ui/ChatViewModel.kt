package com.mrdartsidetm.wasm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrdartsidetm.wasm.data.ChatDao
import com.mrdartsidetm.wasm.data.UserPreferencesRepository
import com.mrdartsidetm.wasm.util.WhatsAppParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class ChatViewModel(
    private val chatDao: ChatDao,
    private val prefs: UserPreferencesRepository,
    val mediaDir: File
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
            withContext(Dispatchers.IO) {
                val lines = content.lines()
                val entities = WhatsAppParser.parse(lines)
                chatDao.clearAll() // Fresh import
                chatDao.insertMessages(entities)
            }
        }
    }

    /**
     * Imports a chat from a ZIP file containing the text file and attached media
     */
    fun importZipFile(inputStream: InputStream) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Create media directory if it doesn't exist, and clear old content
                    if (mediaDir.exists()) {
                        mediaDir.deleteRecursively()
                    }
                    mediaDir.mkdirs()

                    var chatText = ""
                    val zipStream = java.util.zip.ZipInputStream(inputStream)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name
                            val fileName = File(name).name

                            if (fileName.endsWith(".txt", ignoreCase = true) && !name.contains("__MACOSX")) {
                                // Read the chat log text without closing the ZipInputStream
                                val byteStream = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                var len = zipStream.read(buffer)
                                while (len > 0) {
                                    byteStream.write(buffer, 0, len)
                                    len = zipStream.read(buffer)
                                }
                                chatText = byteStream.toString("UTF-8")
                            } else if (!fileName.endsWith(".vcf", ignoreCase = true) && fileName.isNotBlank()) {
                                // Write media file to the media directory
                                val destFile = File(mediaDir, fileName)
                                destFile.outputStream().use { output ->
                                    zipStream.copyTo(output)
                                }
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    if (chatText.isNotEmpty()) {
                        val lines = chatText.lines()
                        val entities = WhatsAppParser.parse(lines)
                        chatDao.clearAll()
                        chatDao.insertMessages(entities)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setIdentity(name: String) {
        viewModelScope.launch {
            prefs.saveUserIdentity(name)
        }
    }
}
