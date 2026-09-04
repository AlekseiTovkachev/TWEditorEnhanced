package app.tweditor

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SaveRepairProbe {
    @Test
    fun scanAndRepair() {
        val environment = SaveSeamSupport.createEnvironment()
        val gameSaves = File("C:\\Users\\atovk\\Documents\\The Witcher\\saves")
        val saves = gameSaves.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
            ?: return org.junit.jupiter.api.Assumptions.assumeTrue(false, "no game saves")
        val sb = StringBuilder()
        var repaired = 0

        for (save in saves) {
            val tempDir = Files.createTempDirectory("repair-check")
            val copy = Files.copy(save.toPath(), tempDir.resolve(save.name)).toFile()
            val intact = try {
                val saveDatabase = SaveDatabase(environment, copy)
                saveDatabase.load()
                val qdbEntry = saveDatabase.getEntry("save.qdb")
                if (qdbEntry == null) {
                    sb.append(save.name).append(": no save.qdb entry\n")
                    false
                } else {
                    val database = Database(environment)
                    qdbEntry.getInputStream().use { database.load(it) }
                    val list = database.getTopLevelStruct()!!.getValue() as DBList
                    val hasQuests = list.getElement("Quests") != null
                    sb.append(save.name).append(": qdb top=").append(list.elementList.joinToString(",") { it.getLabel() })
                        .append(if (hasQuests) "  OK" else "  CORRUPTED").append("\n")
                    hasQuests
                }
            } catch (exc: Throwable) {
                sb.append(save.name).append(": LOAD FAIL ").append(exc.message).append("\n")
                false
            }

            if (!intact && save.name.contains("000016")) {
                val backup = File(save.path + ".bak")
                if (!backup.isFile) {
                    sb.append("  no .bak - cannot repair\n")
                    continue
                }
                val work = Files.copy(backup.toPath(), tempDir.resolve(save.name), StandardCopyOption.REPLACE_EXISTING).toFile()
                val loaded = SaveSeamSupport.load(environment, work, tempDir)
                loaded.session.addJournalEntry("bestiary", "basil/f/1")
                loaded.session.addJournalEntry("bestiary", "boneh/w/1")
                loaded.session.addJournalEntry("bestiary", "arch/s/1")
                SaveSeamSupport.save(loaded)

                val verify = SaveSeamSupport.load(environment, work, tempDir)
                val journalOk = verify.session.getJournalData()!!.entries.any { it.entryId == "arch/s/1" }
                val questsOk = verify.session.getQuests()!!.size > 100
                sb.append("  repair: journalOk=").append(journalOk).append(" questsOk=").append(questsOk).append("\n")
                if (journalOk && questsOk) {
                    val corruptCopy = File(save.path + ".corrupt")
                    Files.copy(save.toPath(), corruptCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    Files.copy(work.toPath(), save.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    repaired++
                    sb.append("  repaired in place (corrupted copy kept as .corrupt)\n")
                }
            }
        }
        sb.insert(0, "repaired=" + repaired + "\n\n")
        Files.writeString(Path.of("C:/Users/atovk/AppData/Local/Temp/opencode/repair-report.txt"), sb.toString())
    }
}
