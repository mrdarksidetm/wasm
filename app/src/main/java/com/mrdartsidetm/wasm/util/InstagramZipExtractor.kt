package com.mrdartsidetm.wasm.util

import com.mrdartsidetm.wasm.data.InstagramAccountEntity
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class InstagramExtractionResult(
    val account: InstagramAccountEntity,
    val baseDir: File,
    val messagesDir: File,
    val chatsFile: File?,
    val extractedFilesCount: Int
)

object InstagramZipExtractor {

    /**
     * Extracts an Instagram export ZIP archive safely with Zip Slip protection.
     * Selectively extracts only message data and associated media, avoiding OOM and conserving storage.
     */
    fun extract(
        inputStream: InputStream,
        targetDir: File,
        fallbackZipName: String? = null,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }
    ): InstagramExtractionResult {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        val canonicalTargetDirPath = targetDir.canonicalPath + File.separator

        onProgress("Scanning and extracting messages & media from ZIP...", 0.1f)

        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        var extractedCount = 0
        var startHereHtmlContent: String? = null
        var chatsHtmlContent: String? = null

        val buffer = ByteArray(8192)

        while (entry != null) {
            val name = entry.name
            val isDirectory = entry.isDirectory

            // Filter out non-message folders to optimize speed and disk usage
            val isRelevant = isRelevantEntry(name)

            if (isRelevant && !isDirectory && !name.contains("__MACOSX")) {
                // Normalize destination path relative to targetDir
                val relativeDest = normalizeRelativePath(name)
                val destFile = File(targetDir, relativeDest)

                // Zip Slip path traversal vulnerability check
                if (destFile.canonicalPath.startsWith(canonicalTargetDirPath)) {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { output ->
                        var len = zipStream.read(buffer)
                        while (len > 0) {
                            output.write(buffer, 0, len)
                            len = zipStream.read(buffer)
                        }
                    }
                    extractedCount++

                    if (destFile.name == "start_here.html" && startHereHtmlContent == null) {
                        try {
                            startHereHtmlContent = destFile.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            // ignore read errors
                        }
                    } else if (destFile.name == "chats.html" && chatsHtmlContent == null) {
                        try {
                            chatsHtmlContent = destFile.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            // ignore read errors
                        }
                    }
                }
            }

            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }

        onProgress("Detecting folder hierarchy & account info...", 0.8f)

        // Locate messages directory inside targetDir
        val messagesDir = locateMessagesDir(targetDir)
        val chatsFile = File(messagesDir, "chats.html").takeIf { it.exists() }
            ?: targetDir.walkTopDown().firstOrNull { it.name == "chats.html" }

        // Read chats.html if not already read
        if (chatsHtmlContent == null && chatsFile?.exists() == true) {
            chatsHtmlContent = chatsFile.readText(Charsets.UTF_8)
        }

        // Detect account info
        val accountHtml = startHereHtmlContent ?: chatsHtmlContent ?: ""
        var account = InstagramHtmlParser.parseAccountInfo(accountHtml, fallbackZipName)

        // Count total conversations found
        val conversationsCount = if (chatsHtmlContent != null) {
            InstagramHtmlParser.parseChatsList(chatsHtmlContent).size
        } else {
            val inboxDir = File(messagesDir, "inbox")
            if (inboxDir.exists()) inboxDir.listFiles()?.count { it.isDirectory } ?: 0 else 0
        }

        account = account.copy(
            totalConversations = conversationsCount
        )

        onProgress("Instagram export ready!", 1.0f)

        return InstagramExtractionResult(
            account = account,
            baseDir = targetDir,
            messagesDir = messagesDir,
            chatsFile = chatsFile,
            extractedFilesCount = extractedCount
        )
    }

    /**
     * Determines whether a ZIP entry is part of the messages or meta landing files.
     */
    private fun isRelevantEntry(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("messages/") ||
                lower.endsWith("start_here.html") ||
                lower.contains("instagram-logo")
    }

    /**
     * Normalizes paths from the ZIP so that messages always align neatly under targetDir.
     * Handles wrapping folder like "instagram-xyz/your_instagram_activity/messages/..." -> "messages/..."
     */
    private fun normalizeRelativePath(name: String): String {
        val clean = name.replace('\\', '/')
        val messagesIndex = clean.indexOf("messages/")
        if (messagesIndex != -1) {
            return clean.substring(messagesIndex)
        }
        if (clean.endsWith("start_here.html")) {
            return "start_here.html"
        }
        if (clean.contains("Instagram-Logo")) {
            return "files/Instagram-Logo.png"
        }
        return clean
    }

    /**
     * Locates the messages directory within targetDir.
     */
    private fun locateMessagesDir(targetDir: File): File {
        val directMessages = File(targetDir, "messages")
        if (directMessages.exists() && directMessages.isDirectory) {
            return directMessages
        }
        val subMessages = targetDir.walkTopDown()
            .firstOrNull { it.isDirectory && it.name == "messages" }
        return subMessages ?: targetDir
    }
}
