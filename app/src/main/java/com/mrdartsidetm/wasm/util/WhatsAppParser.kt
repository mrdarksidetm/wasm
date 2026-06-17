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

    fun parse(lines: List<String>): List<MessageEntity> {
        val parsedMessages = mutableListOf<MessageEntity>()
        var currentMessage: MessageEntity? = null

        for (line in lines) {
            val matcher = messagePattern.matcher(line)
            
            if (matcher.matches()) {
                // If we found a new header, save the previous one and start fresh
                currentMessage?.let { parsedMessages.add(it) }
                
                currentMessage = MessageEntity(
                    timestamp = matcher.group(1) ?: "",
                    sender = matcher.group(2) ?: "",
                    content = matcher.group(3) ?: ""
                )
            } else {
                // If the line doesn't match the pattern, it's a multi-line continuation
                if (currentMessage != null) {
                    currentMessage = currentMessage.copy(
                        content = currentMessage.content + "\n" + line
                    )
                }
            }
        }
        // Add the last message in the file
        currentMessage?.let { parsedMessages.add(it) }
        return parsedMessages
    }
}
