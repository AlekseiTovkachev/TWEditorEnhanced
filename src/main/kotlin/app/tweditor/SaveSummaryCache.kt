package app.tweditor

import java.io.File

/**
 * Remembers the summaries already read for a directory scan so reopening the
 * save picker does not reparse unmodified saves.
 */
class SaveSummaryCache(private val loader: (File) -> SaveSummary) {
    constructor(environment: AppEnvironment) : this({ file -> SaveSummaryReader.read(environment, file) })

    private val summaries = HashMap<String, SaveSummary>()

    fun get(file: File): SaveSummary {
        val key = file.getPath() + "|" + file.lastModified()
        val cached = summaries[key]
        if (cached != null) {
            return cached
        }
        val summary = loader(file)
        if (summaries.size >= MAX_ENTRIES) {
            summaries.clear()
        }
        summaries[key] = summary
        return summary
    }

    companion object {
        private const val MAX_ENTRIES = 128
    }
}
