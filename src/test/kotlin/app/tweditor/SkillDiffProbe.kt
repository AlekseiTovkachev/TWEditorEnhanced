package app.tweditor

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SkillDiffProbe {
    @Test
    fun diffSaves() {
        val environment = SaveSeamSupport.createEnvironment()
        val savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile()
        val before = savesDir.listFiles { _, name -> name.startsWith("000017") }?.firstOrNull()
            ?: return org.junit.jupiter.api.Assumptions.assumeTrue(false, "000017 not found")
        val after = savesDir.listFiles { _, name -> name.startsWith("000018") }?.firstOrNull()
            ?: return org.junit.jupiter.api.Assumptions.assumeTrue(false, "000018 not found")

        val sb = StringBuilder()
        fun load(save: File): Pair<JournalData, Map<String, Int>> {
            val tempDir = Files.createTempDirectory("skill-diff")
            val copy = Files.copy(save.toPath(), tempDir.resolve(save.name)).toFile()
            val loaded = SaveSeamSupport.load(environment, copy, tempDir)
            val journal = loaded.session.getJournalData()!!
            val questStates = loaded.session.getQuests()!!.associate { it.getResourceName() to it.questState }
            return Pair(journal, questStates)
        }

        val (beforeJournal, beforeQuests) = load(before)
        val (afterJournal, afterQuests) = load(after)
        sb.append("before=").append(before.name).append(" entries=").append(beforeJournal.entries.size).append("\n")
        sb.append("after =").append(after.name).append(" entries=").append(afterJournal.entries.size).append("\n\n")

        val beforeKeys = beforeJournal.entries.map { it.category + ":" + it.entryId.lowercase() }.toSet()
        sb.append("=== journal entries ADDED by the skill point ===\n")
        for (entry in afterJournal.entries) {
            val key = entry.category + ":" + entry.entryId.lowercase()
            if (!beforeKeys.contains(key)) {
                sb.append("  ").append(entry.category).append(":").append(entry.entryId)
                    .append(" read=").append(entry.isRead).append("\n")
            }
        }

        val afterKeys = afterJournal.entries.map { it.category + ":" + it.entryId.lowercase() }.toSet()
        sb.append("\n=== journal entries REMOVED ===\n")
        var removed = 0
        for (entry in beforeJournal.entries) {
            val key = entry.category + ":" + entry.entryId.lowercase()
            if (!afterKeys.contains(key)) {
                sb.append("  ").append(entry.category).append(":").append(entry.entryId).append("\n")
                removed++
            }
        }
        if (removed == 0) sb.append("  (none)\n")

        sb.append("\n=== quest state changes ===\n")
        var questChanges = 0
        for ((name, state) in afterQuests) {
            val beforeState = beforeQuests[name]
            if (beforeState != state) {
                sb.append("  ").append(name).append(": ").append(beforeState).append(" -> ").append(state).append("\n")
                questChanges++
            }
        }
        if (questChanges == 0) sb.append("  (none)\n")

        Files.writeString(Path.of("C:/Users/atovk/AppData/Local/Temp/opencode/skill-diff.txt"), sb.toString())
    }
}
