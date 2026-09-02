package com.mrdartsidetm.wasm.util

import com.mrdartsidetm.wasm.data.MessageEntity
import java.util.regex.Pattern

/**
 * WhatsAppParser decodes exported chat transcripts from WhatsApp (.txt exports).
 * It supports:
 * 1. Standard Android / Web exports (e.g. "12/05/23, 14:45 - Sender: Message")
 * 2. iOS bracket exports (e.g. "[12/05/23, 14:45:10] Sender: Message")
 * 3. 12-hour (AM/PM) and 24-hour timestamps, with or without seconds
 * 4. Hidden Unicode control characters (LTR/RTL marks \u200E, \u200F, \uFEFF, etc.)
 * 5. System messages (e.g. encryption notice, group changes, missed calls)
 * 6. Multi-line messages and media attachment references
 */
object WhatsAppParser {

    // Regex for standard export: "Date, Time - (Sender: Message | System Message)"
    // Matches: 1-2 digits/1-2 digits/2-4 digits, time with optional seconds and AM/PM,
    // followed by hyphen/en-dash/em-dash separator, and either "Sender: Content" or "System Content".
    private val standardPattern = Pattern.compile(
        "^(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4},\\s\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[aApP][mM])?)\\s(?:-|–|—)\\s(?:([^:]+):\\s([\\s\\S]*)|(.*))$"
    )

    // Regex for iOS export: "[Date, Time] (Sender: Message | System Message)"
    private val iosPattern = Pattern.compile(
        "^\\[(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4},\\s\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[aApP][mM])?)\\]\\s(?:([^:]+):\\s([\\s\\S]*)|(.*))$"
    )

    // Regex for Android attachment: "filename.ext (file attached)"
    private val androidAttachmentPattern = Pattern.compile("(?i)([^\\n\\r]+?)\\s*\\(file attached\\)")

    // Regex for iOS attachment: "<attached: filename.ext>"
    private val iosAttachmentPattern = Pattern.compile("(?i)<attached:\\s*([^>]+)>")

    /**
     * Cleans hidden Unicode formatting characters commonly inserted by mobile keyboards and WhatsApp.
     */
    private fun sanitizeLine(line: String): String {
        return line
            .replace("\u200E", "") // Left-to-Right mark
            .replace("\u200F", "") // Right-to-Left mark
            .replace("\uFEFF", "") // Byte Order Mark
            .replace("\u202A", "") // Directional embeddings
            .replace("\u202B", "")
            .replace("\u202C", "")
            .replace("\u202D", "")
            .replace("\u202E", "")
            .replace("\u202F", " ") // Narrow no-break space (used before AM/PM)
            .replace("\u00A0", " ") // Non-breaking space
    }

    /**
     * Extracts media file name from message content for Android or iOS export formats.
     */
    fun extractMediaName(content: String): String? {
        val clean = sanitizeLine(content).trim()

        val androidMatcher = androidAttachmentPattern.matcher(clean)
        if (androidMatcher.find()) {
            return androidMatcher.group(1)?.trim()
        }

        val iosMatcher = iosAttachmentPattern.matcher(clean)
        if (iosMatcher.find()) {
            return iosMatcher.group(1)?.trim()
        }

        return null
    }

    /**
     * Parses all lines from an exported WhatsApp text file into a list of MessageEntity.
     */
    fun parse(lines: List<String>): List<MessageEntity> {
        val parsedMessages = mutableListOf<MessageEntity>()
        var currentMessage: MessageEntity? = null

        for (rawLine in lines) {
            val line = sanitizeLine(rawLine).trimEnd()
            if (line.isEmpty() && currentMessage == null) continue

            // Try standard pattern first, then iOS bracket pattern
            var timestamp: String? = null
            var sender: String? = null
            var content: String? = null
            var isSystem = false

            val standardMatcher = standardPattern.matcher(line)
            if (standardMatcher.matches()) {
                timestamp = standardMatcher.group(1) ?: ""
                val groupSender = standardMatcher.group(2)
                if (groupSender != null) {
                    sender = groupSender.trim()
                    content = standardMatcher.group(3) ?: ""
                    isSystem = false
                } else {
                    // System message (no colon separating sender)
                    sender = "System"
                    content = (standardMatcher.group(4) ?: "").trim()
                    isSystem = true
                }
            } else {
                val iosMatcher = iosPattern.matcher(line)
                if (iosMatcher.matches()) {
                    timestamp = iosMatcher.group(1) ?: ""
                    val groupSender = iosMatcher.group(2)
                    if (groupSender != null) {
                        sender = groupSender.trim()
                        content = iosMatcher.group(3) ?: ""
                        isSystem = false
                    } else {
                        sender = "System"
                        content = (iosMatcher.group(4) ?: "").trim()
                        isSystem = true
                    }
                }
            }

            if (timestamp != null && sender != null && content != null) {
                // Found a new message header: commit the previous message if any
                currentMessage?.let { parsedMessages.add(it) }

                currentMessage = MessageEntity(
                    timestamp = timestamp,
                    sender = sender,
                    content = content,
                    isSystemMessage = isSystem,
                    mediaName = extractMediaName(content)
                )
            } else {
                // Continuation line of multi-line message
                if (currentMessage != null) {
                    val newContent = currentMessage.content + "\n" + line
                    currentMessage = currentMessage.copy(
                        content = newContent,
                        mediaName = extractMediaName(newContent) ?: currentMessage.mediaName
                    )
                }
            }
        }

        // Add the trailing message
        currentMessage?.let { parsedMessages.add(it) }
        return parsedMessages
    }
}
