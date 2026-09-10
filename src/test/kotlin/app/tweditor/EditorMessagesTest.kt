package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ListResourceBundle
import java.util.ResourceBundle

class EditorMessagesTest {
    @Test
    fun theEnglishBundleBacksTheDefaultLanguage() {
        assertEquals("Storage", EditorMessages.english().get("storage.title"))
        assertEquals("Storage", EditorMessages.forLanguage(3).get("storage.title"))
    }

    @Test
    fun theRussianBundleResolvesRussianText() {
        val russian = EditorMessages.forLanguage(14)
        assertEquals(14, russian.languageId)
        assertEquals("Хранилище", russian.get("storage.title"))
    }

    @Test
    fun argumentsFormatThroughTheBundle() {
        assertEquals(
            "3 stored item(s); double-click to inspect/edit",
            EditorMessages.english().get("storage.count", 3)
        )
        assertEquals(
            "Предметов в хранилище: 3; двойной щелчок — просмотр и правка",
            EditorMessages.forLanguage(14).get("storage.count", 3)
        )
    }

    @Test
    fun aKeyMissingEverywhereYieldsTheKeyItself() {
        assertEquals("no.such.key", EditorMessages.english().get("no.such.key"))
    }

    @Test
    fun aMissingKeyFallsBackThroughTheBundleParentChain() {
        val english = object : ListResourceBundle() {
            override fun getContents(): Array<Array<out Any?>> = arrayOf(arrayOf("shell.only", "English text"))
        }
        val russian = object : ListResourceBundle() {
            init {
                parent = english
            }

            override fun getContents(): Array<Array<out Any?>> = arrayOf(arrayOf("ru.only", "Русский текст"))
        }

        val messages = EditorMessages(14, russian)
        assertEquals("Русский текст", messages.get("ru.only"))
        assertEquals("English text", messages.get("shell.only"))
    }

    @Test
    fun theRussianBundleCarriesEveryEnglishKey() {
        val english = ResourceBundle.getBundle(EditorMessages.BUNDLE_BASE_NAME, java.util.Locale.ROOT)
        val russian = ResourceBundle.getBundle(EditorMessages.BUNDLE_BASE_NAME, java.util.Locale("ru"))
        val missing = english.keys.asSequence().filter { key -> !russian.keySet().contains(key) }.toList()
        assertTrue(missing.isEmpty(), "Messages_ru.properties is missing keys: $missing")
    }
}
