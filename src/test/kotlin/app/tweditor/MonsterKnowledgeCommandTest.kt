package app.tweditor

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(300)
class MonsterKnowledgeCommandTest {
    @Test
    fun bookStyleGrantIsUndoableAndPersistsItsExactMetadata(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val controller = EditorCommandController(loaded.session)

        val result = controller.dispatch(GrantMonsterKnowledgeCommand("ghoul"))
        assertTrue(result is EditorCommandResult.Applied)
        val applied = result as EditorCommandResult.Applied
        assertEquals(EvidenceLevel.UNVERIFIED, applied.change.evidence)
        assertEquals("journal.pending.grantMonster", applied.change.description.key)
        assertEquals(listOf<Any>("Ghoul"), applied.change.description.args)
        val added = journalStructs(loaded).first { it.getString("Entry") == "bestiary:ghoul/s/1" }
        assertEquals(0, added.getInteger("EntryCD"))
        assertEquals(0, added.getInteger("EntryTOD"))
        assertEquals(0, added.getInteger("EntryRead"))

        assertTrue(controller.undo().completed)
        assertFalse(journalEntries(loaded).any { it.category == "bestiary" && it.entryId == "ghoul/s/1" })
        assertFalse(loaded.session.isJournalDirty())

        assertTrue(controller.dispatch(GrantMonsterKnowledgeCommand("ghoul")) is EditorCommandResult.Applied)
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(repacked),
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
        )
        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        assertTrue(journalEntries(reloaded).any { it.category == "bestiary" && it.entryId == "ghoul/s/1" })
    }

    @Test
    fun duplicateGrantIsRejectedAndRemovalDeletesOnlyTheSelectedMonstersVariants(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        loaded.session.addJournalEntry("bestiary", "ghoul/w/1")
        loaded.session.addJournalEntry("bestiary", "ghoul/b/1")
        loaded.session.addJournalEntry("bestiary", "drowner/w/1")
        val controller = EditorCommandController(loaded.session)

        val duplicate = controller.dispatch(GrantMonsterKnowledgeCommand("ghoul"))
        assertTrue(duplicate is EditorCommandResult.Rejected)
        assertEquals(3, journalEntries(loaded).count { it.category == "bestiary" })

        val removed = controller.dispatch(RemoveMonsterKnowledgeCommand("ghoul"))
        assertTrue(removed is EditorCommandResult.Applied)
        assertEquals(1, journalEntries(loaded).count { it.category == "bestiary" })
        assertTrue(controller.undo().completed)
        assertEquals(3, journalEntries(loaded).count { it.category == "bestiary" })
        assertTrue(controller.dispatch(RemoveMonsterKnowledgeCommand("ghoul")) is EditorCommandResult.Applied)
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(repacked),
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
        )
        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val entries = journalEntries(reloaded)
        assertTrue(entries.any { it.category == "bestiary" && it.entryId == "drowner/w/1" })
        assertFalse(entries.any { it.category == "bestiary" && it.entryId.startsWith("ghoul/") })
        assertTrue(entries.any { it.category == "character" }, "unrelated Journal metadata must remain")
    }

    @Test
    fun catalogMergesObservedVariantsEvenWhenTheInstalledCatalogIsAbsent() {
        val targets = JournalCatalog.monsterTargets(
            null,
            listOf(
                JournalEntry("bestiary", "ghoul/w/1", false),
                JournalEntry("bestiary", "ghoul/s/1", false)
            )
        )
        assertEquals(listOf("ghoul/s/1", "ghoul/w/1"), targets.single().variants)
        assertTrue(targets.single().known)
    }

    private fun journalEntries(loaded: SaveSeamSupport.Loaded): List<JournalEntry> =
        loaded.session.getJournalData()!!.entries.toList()

    private fun journalStructs(loaded: SaveSeamSupport.Loaded): List<DBList> {
        val top = loaded.session.getQuestDatabase()!!.getTopLevelStruct()!!.getValue() as DBList
        val list = top.getElement("Journal")!!.getValue() as DBList
        return list.map { it.getValue() as DBList }
    }
}
