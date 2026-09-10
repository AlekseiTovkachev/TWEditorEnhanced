package app.tweditor

import java.io.File

/** A content language the editor can display: a TLK id plus its native name. */
data class EditorLanguage(val id: Int, val displayName: String)

/**
 * Discovers the content languages an install offers (dialog_<id>.tlk files,
 * loose files winning over archive entries) and resolves display names from
 * the game's own languages.2da, falling back to the standard TLK id table.
 */
object LanguageCatalog {
    const val ENGLISH_LANGUAGE_ID = 3
    const val RUSSIAN_LANGUAGE_ID = 14
    const val LANGUAGE_FILE_PREFIX = "dialog_"
    const val LANGUAGE_FILE_SUFFIX = ".tlk"

    private val CONTENT_TLK_NAME = Regex("dialog_(\\d+)\\.tlk")

    /** Native display names for the known TLK ids, used when languages.2da is absent. */
    private val ID_NAMES = mapOf(
        0 to "Debug",
        3 to "English",
        5 to "Polski",
        10 to "Deutsch",
        11 to "Français",
        12 to "Español",
        13 to "Italiano",
        14 to "Русский",
        15 to "Čeština",
        16 to "Magyar",
        20 to "한국어",
        21 to "中文(繁體)",
        22 to "中文(简体)"
    )

    /** Native names for the language labels languages.2da carries. */
    private val LABEL_NAMES = mapOf(
        "english" to "English",
        "polish" to "Polski",
        "german" to "Deutsch",
        "french" to "Français",
        "spanish" to "Español",
        "italian" to "Italiano",
        "russian" to "Русский",
        "czech" to "Čeština",
        "hungarian" to "Magyar",
        "korean" to "한국어",
        "chinesetrad" to "中文(繁體)",
        "chinesesimp" to "中文(简体)",
        "debug" to "Debug"
    )

    /** Content languages available in the install, ordered by TLK id. */
    fun discover(environment: AppEnvironment): List<EditorLanguage> {
        val ids = LinkedHashSet<Int>()
        val dataPath = environment.installDataPath
        if (dataPath != null) {
            val dataDirectory = File(dataPath)
            dataDirectory.listFiles { file ->
                val name = file.name.lowercase()
                name.startsWith(LANGUAGE_FILE_PREFIX) && name.endsWith(LANGUAGE_FILE_SUFFIX)
            }?.forEach { file ->
                CONTENT_TLK_NAME.matchEntire(file.name.lowercase())?.groupValues?.get(1)?.toIntOrNull()
                    ?.let(ids::add)
            }
        }
        for (name in environment.resourceFiles.keys) {
            CONTENT_TLK_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let(ids::add)
        }
        val languages2da = dataPath?.let { File(it, "languages.2da") }
        return ids.sorted().map { id -> EditorLanguage(id, displayNameFor(id, languages2da)) }
    }

    /** The display language for an id, reusing discovery when it already names it. */
    fun currentLanguage(environment: AppEnvironment, languageId: Int): EditorLanguage =
        discover(environment).firstOrNull { it.id == languageId }
            ?: EditorLanguage(languageId, displayNameFor(languageId, environment.installDataPath?.let { File(it, "languages.2da") }))

    /** Display name for a TLK id: languages.2da label when recognized, else the standard table. */
    fun displayNameFor(id: Int, languages2da: File?): String {
        if (languages2da != null && languages2da.isFile) {
            val label = languages2daLabels(languages2da)[id]
            if (label != null) {
                val canonical = canonicalLabel(label)
                if (canonical != null) {
                    LABEL_NAMES[canonical]?.let { return it }
                }
            }
        }
        return ID_NAMES[id] ?: "Language $id"
    }

    /**
     * Parses a 2DA V2.0 table into id → label rows. Standard shape: header,
     * a blank line, the column-name line, then rows whose first (unlabeled)
     * token is the row index and whose labeled columns follow.
     */
    fun languages2daLabels(file: File): Map<Int, String> {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        if (lines.size < 3 || !lines[0].trimStart().startsWith("2DA")) {
            return emptyMap()
        }
        val columns = lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("2DA") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?: return emptyMap()
        val idColumn = columns.indexOf("id")
        val nameColumn = columns.indexOf("name")
        if (idColumn < 0 || nameColumn < 0) {
            return emptyMap()
        }
        val headerLine = lines.indexOfFirst { it.isNotBlank() && !it.trimStart().startsWith("2DA") }
        val labels = LinkedHashMap<Int, String>()
        for (line in lines.subList(headerLine + 1, lines.size)) {
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size <= maxOf(idColumn, nameColumn) + 1) {
                continue
            }
            val id = tokens[idColumn + 1].toIntOrNull() ?: continue
            val label = tokens[nameColumn + 1]
            if (label != "****") {
                labels[id] = label
            }
        }
        return labels
    }

    private fun canonicalLabel(label: String): String {
        var canonical = label.trim().lowercase()
        if (canonical.startsWith("final")) {
            canonical = canonical.substring("final".length)
        }
        for (suffix in listOf("_short", "_long")) {
            if (canonical.endsWith(suffix)) {
                canonical = canonical.removeSuffix(suffix)
            }
        }
        return canonical
    }
}
