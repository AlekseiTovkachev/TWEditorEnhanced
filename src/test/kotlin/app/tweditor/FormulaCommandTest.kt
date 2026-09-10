package app.tweditor

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(300)
class FormulaCommandTest {
    @Test
    fun potionGrantSynchronizesAllRepresentationsAndRoundTrips(@TempDir tempDir: Path) {
        grantRoundTrip(tempDir, FormulaKind.POTION, "it_potion_777")
    }

    @Test
    fun oilGrantSynchronizesAllRepresentationsAndRoundTrips(@TempDir tempDir: Path) {
        grantRoundTrip(tempDir, FormulaKind.OIL, "it_grease_777")
    }

    @Test
    fun bombGrantSynchronizesAllRepresentationsAndRoundTrips(@TempDir tempDir: Path) {
        grantRoundTrip(tempDir, FormulaKind.BOMB, "it_bomb_777")
    }

    @Test
    fun formulaRemovalRequiresAdvancedEditingAndIsUndoable(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val controller = EditorCommandController(loaded.session)
        val id = "it_potion_778"

        assertTrue(controller.dispatch(GrantFormulaCommand(FormulaKind.POTION.category, id, 123456)) is EditorCommandResult.Applied)
        val locked = controller.dispatch(RemoveFormulaCommand(FormulaKind.POTION.category, id))
        assertTrue(locked is EditorCommandResult.Rejected)
        assertEquals("journal.command.removeRequiresAdvanced", (locked as EditorCommandResult.Rejected).problems.single().key)
        assertFormulaPresent(loaded, FormulaKind.POTION, id, 123456)

        val removed = controller.dispatch(RemoveFormulaCommand(FormulaKind.POTION.category, id, advancedJournalEditing = true))
        assertTrue(removed is EditorCommandResult.Applied)
        assertEquals(EvidenceLevel.DELIBERATELY_DANGEROUS, (removed as EditorCommandResult.Applied).change.evidence)
        assertFormulaAbsent(loaded, FormulaKind.POTION, id)
        assertTrue(controller.undo().completed)
        assertFormulaPresent(loaded, FormulaKind.POTION, id, 123456)
    }

    private fun grantRoundTrip(tempDir: Path, kind: FormulaKind, id: String) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val controller = EditorCommandController(loaded.session)
        val acquisitionTime = 123456

        val result = controller.dispatch(GrantFormulaCommand(kind.category, id, acquisitionTime))
        assertTrue(result is EditorCommandResult.Applied)
        assertEquals(EvidenceLevel.UNVERIFIED, (result as EditorCommandResult.Applied).change.evidence)
        assertFormulaPresent(loaded, kind, id, acquisitionTime)

        val duplicate = controller.dispatch(GrantFormulaCommand(kind.category, id, acquisitionTime))
        assertTrue(duplicate is EditorCommandResult.Rejected)
        assertTrue(controller.undo().completed)
        assertFormulaAbsent(loaded, kind, id)

        assertTrue(controller.dispatch(GrantFormulaCommand(kind.category, id, acquisitionTime)) is EditorCommandResult.Applied)
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        assertFormulaPresent(reloaded, kind, id, acquisitionTime)
        assertFalse(hasLabel(reloaded.session.playerDatabase!!.getTopLevelStruct()!!, "READBOOK_lst"))
        SaveSeamSupport.assertUntouchedEntries(
            before,
            SaveSeamSupport.entryDigests(reloaded.saveDatabase!!),
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!, loaded.questDBName + ".qdb")
        )
    }

    private fun assertFormulaPresent(loaded: SaveSeamSupport.Loaded, kind: FormulaKind, id: String, tod: Int) {
        for (player in listOf(HeroData.playerFields(loaded.session)!!, loaded.session.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList)) {
            assertTrue(alchemyIds(player, "AlchIdent").contains(id), "AlchIdent must contain $id")
            assertTrue(alchemyIds(player, "AlchKnowledge").contains(id), "AlchKnowledge must contain $id")
            assertTrue(alchemyIds(player, "AlchKnwnRandRec").contains(id), "AlchKnwnRandRec must contain $id")
        }
        val journalElement = loaded.session.getQuestDatabase()!!.getTopLevelStruct()!!.getValue() as DBList
        val journal = journalElement.getElement("Journal")!!.getValue() as DBList
        val entry = journal.map { it.getValue() as DBList }.single {
            it.getString("Entry").equals("${kind.category}:$id", ignoreCase = true)
        }
        assertEquals(0, entry.getInteger("EntryCD"))
        assertEquals(tod, entry.getInteger("EntryTOD"))
        assertEquals(0, entry.getInteger("EntryRead"))
    }

    private fun assertFormulaAbsent(loaded: SaveSeamSupport.Loaded, kind: FormulaKind, id: String) {
        assertFalse(loaded.session.getJournalData()!!.entries.any {
            it.category == kind.category && it.entryId.equals(id, ignoreCase = true)
        })
        for (player in listOf(HeroData.playerFields(loaded.session)!!, loaded.session.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList)) {
            assertFalse(alchemyIds(player, "AlchIdent").contains(id))
            assertFalse(alchemyIds(player, "AlchKnowledge").contains(id))
            assertFalse(alchemyIds(player, "AlchKnwnRandRec").contains(id))
        }
    }

    private fun alchemyIds(player: DBList, label: String): Set<String> {
        val list = player.getElement(label)!!.getValue() as DBList
        return list.mapNotNull { element ->
            val fields = element.getValue() as DBList
            if (label == "AlchIdent") {
                fields.getString("AlchSubstance")
            } else {
                fields.getString("AlchRecipName").ifEmpty { fields.getString("AlchRecipTemp") }
            }.takeIf { it.isNotEmpty() }
        }.toSet()
    }

    private fun hasLabel(element: DBElement, label: String): Boolean {
        if (element.getLabel().equals(label, ignoreCase = true)) return true
        return when (element.getType()) {
            DBElement.LIST, DBElement.STRUCT -> (element.getValue() as DBList).any { hasLabel(it, label) }
            else -> false
        }
    }
}
