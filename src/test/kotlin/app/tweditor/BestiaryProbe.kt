package app.tweditor

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class BestiaryProbe {
    @Test
    fun probeBrokenQdb() {
        val gameSaves = File("C:\\Users\\atovk\\Documents\\The Witcher\\saves")
        val saves = gameSaves.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
            ?: return org.junit.jupiter.api.Assumptions.assumeTrue(false, "no game saves")
        val target = saves.firstOrNull { it.name.contains("000016") }
            ?: return org.junit.jupiter.api.Assumptions.assumeTrue(false, "000016 not found")

        val environment = SaveSeamSupport.createEnvironment()
        val tempDir = Files.createTempDirectory("bestiary-probe")
        val copy = Files.copy(target.toPath(), tempDir.resolve(target.name)).toFile()
        val saveDatabase = SaveDatabase(environment, copy)
        saveDatabase.load()
        val sb = StringBuilder()
        sb.append("save=").append(target.name).append("\n")

        val qdbEntry = saveDatabase.getEntry("save.qdb")
        if (qdbEntry == null) {
            sb.append("save.qdb entry MISSING\n")
            sb.append("all entries:\n")
            for (entry in saveDatabase.entries) {
                sb.append("  ").append(entry.resourceName).append("\n")
            }
        } else {
            val bytes = qdbEntry.getInputStream().use { it.readBytes() }
            sb.append("save.qdb size=").append(bytes.size).append("\n")
            val database = Database(environment)
            database.load(qdbEntry.getInputStream())
            val top = database.getTopLevelStruct()!!
            val list = top.getValue() as DBList
            sb.append("top struct type=").append(top.getType()).append(" label=[").append(top.getLabel()).append("]\n")
            sb.append("top-level fields:\n")
            for (el in list) {
                val value = el.getValue()
                val detail = if (value is DBList) "count=" + value.getElementCount() else value.toString()
                sb.append("  ").append(el.getLabel()).append(" type=").append(el.getType()).append(" ").append(detail).append("\n")
            }
            val journalElement = list.getElement("Journal")
            if (journalElement != null) {
                sb.append("\nJournal entries:\n")
                for (el in journalElement.getValue() as DBList) {
                    val fields = el.getValue() as DBList
                    sb.append("  ").append(fields.getString("Entry")).append(" read=").append(fields.getInteger("EntryRead")).append("\n")
                }
            }
        }
        Files.writeString(Path.of("C:/Users/atovk/AppData/Local/Temp/opencode/bestiary-probe.txt"), sb.toString())
    }
}
