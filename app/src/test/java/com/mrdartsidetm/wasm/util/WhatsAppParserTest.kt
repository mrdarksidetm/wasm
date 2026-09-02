package com.mrdartsidetm.wasm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppParserTest {

    @Test
    fun testStandardAndroidFormat() {
        val input = listOf(
            "12/05/23, 14:45 - Alice: Hey how are you?",
            "12/05/23, 14:46 - Bob: I am doing great! Thanks for asking."
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)

        assertEquals("Alice", messages[0].sender)
        assertEquals("Hey how are you?", messages[0].content)
        assertEquals("12/05/23, 14:45", messages[0].timestamp)
        assertEquals(false, messages[0].isSystemMessage)

        assertEquals("Bob", messages[1].sender)
        assertEquals("I am doing great! Thanks for asking.", messages[1].content)
    }

    @Test
    fun test12HourFormatWithAmPm() {
        val input = listOf(
            "15/08/2023, 2:30 pm - John: Afternoon check-in",
            "15/08/2023, 11:15 AM - Sarah: Good morning"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)
        assertEquals("John", messages[0].sender)
        assertEquals("Afternoon check-in", messages[0].content)
        assertEquals("Sarah", messages[1].sender)
    }

    @Test
    fun testIosBracketFormat() {
        val input = listOf(
            "[12/05/23, 14:45:10] Alice: Sent from iPhone",
            "[12/05/2023, 2:45:10 PM] Bob: Got it"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)
        assertEquals("Alice", messages[0].sender)
        assertEquals("Sent from iPhone", messages[0].content)
        assertEquals("12/05/23, 14:45:10", messages[0].timestamp)
        assertEquals("Bob", messages[1].sender)
        assertEquals("Got it", messages[1].content)
    }

    @Test
    fun testHiddenUnicodeControlCharacters() {
        // WhatsApp on iOS often prepends \u200E (LTR mark)
        val input = listOf(
            "\u200E[12/05/23, 14:45:10] Alice: Hello with LTR marker",
            "\u200E12/05/23, 14:46 - Bob: Response with LTR marker"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)
        assertEquals("Alice", messages[0].sender)
        assertEquals("Hello with LTR marker", messages[0].content)
        assertEquals("Bob", messages[1].sender)
        assertEquals("Response with LTR marker", messages[1].content)
    }

    @Test
    fun testSystemMessages() {
        val input = listOf(
            "12/05/23, 14:45 - Messages and calls are end-to-end encrypted. No one outside of this chat can read or listen.",
            "12/05/23, 14:46 - Alice: Hi everyone!"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)
        assertEquals("System", messages[0].sender)
        assertTrue(messages[0].isSystemMessage)
        assertEquals(false, messages[1].isSystemMessage)
        assertEquals("Alice", messages[1].sender)
    }

    @Test
    fun testMultiLineMessage() {
        val input = listOf(
            "12/05/23, 14:45 - Alice: Paragraph one",
            "Paragraph two of the same message",
            "Paragraph three",
            "12/05/23, 14:47 - Bob: Got all paragraphs!"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(2, messages.size)
        assertEquals("Paragraph one\nParagraph two of the same message\nParagraph three", messages[0].content)
        assertEquals("Bob", messages[1].sender)
    }

    @Test
    fun testMediaAttachmentDetection() {
        val input = listOf(
            "12/05/23, 14:45 - Alice: IMG-20230814-WA0001.jpg (file attached)",
            "[12/05/23, 14:46:00] Bob: <attached: 00000002-PHOTO.jpg>",
            "12/05/23, 14:47 - Alice: document.pdf (file attached)"
        )
        val messages = WhatsAppParser.parse(input)
        assertEquals(3, messages.size)
        assertEquals("IMG-20230814-WA0001.jpg", messages[0].mediaName)
        assertEquals("00000002-PHOTO.jpg", messages[1].mediaName)
        assertEquals("document.pdf", messages[2].mediaName)
    }
}
