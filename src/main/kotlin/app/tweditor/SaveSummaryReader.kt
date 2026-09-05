package app.tweditor

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

object SaveSummaryReader {
    fun read(environment: AppEnvironment, file: File): SaveSummary {
        val saveDatabase = SaveDatabase(environment, file)
        saveDatabase.load()
        return SaveSummary(
            file,
            readSaveName(saveDatabase, file),
            readLevel(environment, saveDatabase),
            readScreenshot(saveDatabase),
            file.lastModified()
        )
    }

    private fun readSaveName(saveDatabase: SaveDatabase, file: File): String {
        val entry = saveDatabase.getEntry("savenfo.txt") ?: return fallbackName(file)
        val text = try {
            entry.getInputStream().use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
        } catch (exc: IOException) {
            return fallbackName(file)
        }
        val trimmed = text.trim('\u0000', ' ', '\t', '\r', '\n')
        return if (trimmed.isEmpty()) fallbackName(file) else trimmed
    }

    private fun fallbackName(file: File): String {
        val name = file.getName()
        val sep = name.lastIndexOf('.')
        return if (sep > 0) name.substring(0, sep) else name
    }

    private fun readLevel(environment: AppEnvironment, saveDatabase: SaveDatabase): Int {
        val entry = saveDatabase.getEntry("player.utc") ?: return -1
        return try {
            val database = Database(environment)
            entry.getInputStream().use { input ->
                database.load(input)
            }
            val topLevel = database.getTopLevelStruct()
            val list = topLevel?.getValue() as? DBList ?: return -1
            list.getInteger("ExpLevel")
        } catch (exc: DBException) {
            -1
        } catch (exc: IOException) {
            -1
        }
    }

    private fun readScreenshot(saveDatabase: SaveDatabase): TgaImage? {
        val entry = saveDatabase.entries.firstOrNull { it.resourceName.endsWith(".tga") }
            ?: return null
        return try {
            entry.getInputStream().use { input ->
                TgaDecoder.decode(input)
            }
        } catch (exc: IOException) {
            null
        }
    }
}
