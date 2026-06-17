package com.mrdartsidetm.wasm.util

import com.mrdartsidetm.wasm.data.MessageEntity
import java.util.regex.Pattern

object WhatsAppParser {
    /**
     * This Regex pattern looks for:
     * 1. A date/time at the start: [0-9/]+, [0-9:]+
     * 2. A separator: " - "
     * 3. A sender name followed by a colon: ([^:]+):
     * 4. The message body: (.*)
     */
    private val messagePattern = Pattern.compile("^(\\d{1,2}/\\d{1,2}/\\d{2,4},\\s\\d{1,2}:\\d{2})\\s-\\s([^:]+):\\s(.*)$")

    private fun extractMediaName(content: String): String? {
        // Android format: "IMG-20230814-WA0001.jpg (file attached)"
        if (content.endsWith(" (file attached)")) {
            return content.substring(0, content.length - " (file attached)".length).trim()
        }
        // iOS format: "<attached: IMG-20230814-WA0001.jpg>" (and strip left-to-right mark U+200E)
        val cleanContent = content.replace("\u200E", "")
        if (cleanContent.startsWith("<attached:") && cleanContent.endsWith(">")) {
            return cleanContent.substring("<attached:".length, cleanContent.length - 1).trim()
        }
        return null
    }

    fun parse(lines: List<String>): List<MessageEntity> {
        val parsedMessages = mutableListOf<MessageEntity>()
        var currentMessage: MessageEntity? = null

        for (line in lines) {
            val matcher = messagePattern.matcher(line)
            
            if (matcher.matches()) {
                // If we found a new header, save the previous one and start fresh
                currentMessage?.let { parsedMessages.add(it) }
                
                val content = matcher.group(3) ?: ""
                currentMessage = MessageEntity(
                    timestamp = matcher.group(1) ?: "",
                    sender = matcher.group(2) ?: "",
                    content = content,
                    mediaName = extractMediaName(content)
                )
            } else {
                // If the line doesn't match the pattern, it's a multi-line continuation
                if (currentMessage != null) {
                    val newContent = currentMessage.content + "\n" + line
                    currentMessage = currentMessage.copy(
                        content = newContent,
                        mediaName = extractMediaName(newContent)
                    )
                }
            }
        }
        // Add the last message in the file
        currentMessage?.let { parsedMessages.add(it) }
        return parsedMessages
    }
}
