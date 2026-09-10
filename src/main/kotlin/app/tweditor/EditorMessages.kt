package app.tweditor

import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

/**
 * The editor shell's translations: a ResourceBundle whose parent chain falls
 * back from the selected language to the English base (Messages.properties).
 * Shell code reads it through LocalEditorMessages; the Seam only ever sees
 * message keys as data and never renders.
 */
class EditorMessages internal constructor(
    val languageId: Int,
    private val bundle: ResourceBundle
) {
    /** The translated text for [key], formatted with [args], or the key itself when untranslatable. */
    fun get(key: String, vararg args: Any?): String {
        val pattern = try {
            bundle.getString(key)
        } catch (_: MissingResourceException) {
            return key
        }
        if (args.isEmpty()) {
            return pattern
        }
        return MessageFormat(pattern, Locale.ROOT).format(args, StringBuffer(), null).toString()
    }

    /** Renders a LocalizedText (and any nested ones) through this bundle. */
    fun render(text: LocalizedText): String {
        val resolved = text.args.map { arg -> if (arg is LocalizedText) render(arg) else arg }
        return get(text.key, *resolved.toTypedArray())
    }

    /** Renders a message list for the error strip; multi-entry lists become bulleted. */
    fun renderErrorStrip(texts: List<LocalizedText>): String {
        if (texts.isEmpty()) {
            return ""
        }
        val rendered = texts.map(::render)
        if (texts.size == 1) {
            return rendered[0]
        }
        return rendered[0] + rendered.drop(1).joinToString(prefix = "\n• ", separator = "\n• ")
    }

    override fun toString(): String = "EditorMessages(languageId=$languageId)"

    companion object {
        const val BUNDLE_BASE_NAME = "app.tweditor.Messages"

        /**
         * Shell translations shipped with the editor, keyed by content TLK id.
         * A language the editor cannot fully display is never offered in the
         * header switch.
         */
        private val SHELL_TRANSLATIONS = mapOf(
            LanguageCatalog.ENGLISH_LANGUAGE_ID to Locale.ROOT,
            LanguageCatalog.RUSSIAN_LANGUAGE_ID to Locale("ru")
        )

        fun localeFor(languageId: Int): Locale = SHELL_TRANSLATIONS[languageId] ?: Locale.ROOT

        /** True when a shell translation exists for the content language. */
        fun isAvailable(languageId: Int): Boolean = SHELL_TRANSLATIONS.containsKey(languageId)

        fun forLanguage(languageId: Int): EditorMessages =
            EditorMessages(languageId, ResourceBundle.getBundle(BUNDLE_BASE_NAME, localeFor(languageId)))

        fun english(): EditorMessages = EditorMessages(
            LanguageCatalog.ENGLISH_LANGUAGE_ID,
            ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT)
        )
    }
}
