package app.tweditor

import java.io.File

/** Small parser cache for callers that scan a save directory. */
class SaveSummaryCache(private val loader: (File) -> SaveSummary) {
    constructor(environment: AppEnvironment) : this({ file -> SaveSummaryReader.read(environment, file) })

    private val summaries = HashMap<String, SaveSummary>()

    fun get(file: File): SaveSummary {
        val key = file.path + "|" + file.lastModified()
        summaries[key]?.let { return it }
        val summary = loader(file)
        if (summaries.size >= MAX_ENTRIES) summaries.clear()
        summaries[key] = summary
        return summary
    }

    companion object {
        private const val MAX_ENTRIES = 128
    }
}
