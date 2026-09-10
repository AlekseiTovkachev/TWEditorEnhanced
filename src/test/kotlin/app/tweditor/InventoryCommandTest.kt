package app.tweditor

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(300)
class InventoryCommandTest {
    @Test
    fun carriedDestinationsUseOneSharedPickerClassificationAndRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        loaded.session.createBaseline()
        val environment = loaded.environment
        val source = loaded.player!!.getElement("ItemList")!!.getValue() as DBList
        val seed = source.getElement(0).getValue() as DBList
        addTemplate(environment, seed, "test_satchel", "Test satchel item", quest = false, alchemy = false)
        addTemplate(environment, seed, "test_alchemy", "Test alchemy ingredient", quest = false, alchemy = true)
        addTemplate(environment, seed, "test_quest", "Test quest item", quest = true, alchemy = false)
        addTemplate(environment, seed, "test_weapon", "Test weapon", quest = false, alchemy = false, baseItem = 1)

        assertTrue(InventoryCatalog.pickerItems(environment, InventoryDestination.SATCHEL).any { it.resourceName == "test_satchel" })
        assertFalse(InventoryCatalog.pickerItems(environment, InventoryDestination.SATCHEL).any { it.resourceName == "test_alchemy" })
        assertFalse(InventoryCatalog.pickerItems(environment, InventoryDestination.SATCHEL).any { it.resourceName == "test_quest" })
        // Weapons belong in Equipment, never in the satchel.
        assertFalse(InventoryCatalog.pickerItems(environment, InventoryDestination.SATCHEL).any { it.resourceName == "test_weapon" })
        assertTrue(InventoryCatalog.pickerItems(environment, InventoryDestination.STORAGE).any { it.resourceName == "test_weapon" })
        assertEquals(
            setOf("test_alchemy"),
            InventoryCatalog.pickerItems(environment, InventoryDestination.ALCHEMY)
                .filter { it.resourceName.startsWith("test_") }
                .map { it.resourceName }
                .toSet()
        )
        assertEquals(
            setOf("test_quest"),
            InventoryCatalog.pickerItems(environment, InventoryDestination.QUEST_ITEMS)
                .filter { it.resourceName.startsWith("test_") }
                .map { it.resourceName }
                .toSet()
        )

        val controller = EditorCommandController(loaded.session)
        assertApplied(controller.dispatch(AddInventoryItemCommand(environment, InventoryDestination.SATCHEL, "test_satchel")))
        assertApplied(controller.dispatch(AddInventoryItemCommand(environment, InventoryDestination.ALCHEMY, "test_alchemy")))
        assertApplied(controller.dispatch(AddInventoryItemCommand(environment, InventoryDestination.QUEST_ITEMS, "test_quest")))

        assertTrue(controller.undo().completed, "the last carried-item add must be undoable")
        assertFalse(InventoryViewState.from(loaded.session).questItems.any { it.templateResRef == "test_quest" })
        assertApplied(controller.dispatch(AddInventoryItemCommand(environment, InventoryDestination.QUEST_ITEMS, "test_quest")))

        val afterApply = controller.apply()
        assertTrue(afterApply.completed)
        assertFalse(loaded.session.isDraftDirty())

        // A second edit is reverted to the applied baseline without losing the
        // three carried-item additions that were already applied.
        val satchelCountBeforeSecond = InventoryViewState.from(loaded.session).satchel.cells.count { it?.templateResRef == "test_satchel" }
        val secondEdit = controller.dispatch(
            AddInventoryItemCommand(environment, InventoryDestination.SATCHEL, "test_satchel")
        )
        assertApplied(secondEdit)
        assertTrue(controller.revert().completed)
        assertEquals(
            satchelCountBeforeSecond,
            InventoryViewState.from(loaded.session).satchel.cells.count { it?.templateResRef == "test_satchel" }
        )

        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), setOf(loaded.modName!!))

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedView = InventoryViewState.from(reloaded.session)
        assertTrue(reloadedView.satchel.cells.any { it?.templateResRef == "test_satchel" })
        assertTrue(reloadedView.alchemy.cells.any { it?.templateResRef == "test_alchemy" })
        assertTrue(reloadedView.questItems.any { it.templateResRef == "test_quest" })

        val reloadedItems = reloaded.player!!.getElement("ItemList")!!.getValue() as DBList
        val satchelFields = reloadedItems.first { (it.getValue() as DBList).getString("TemplateResRef") == "test_satchel" }.getValue() as DBList
        val alchemyFields = reloadedItems.first { (it.getValue() as DBList).getString("TemplateResRef") == "test_alchemy" }.getValue() as DBList
        assertTrue(satchelFields.getInteger("Repos_PosY") in 0..2)
        assertTrue(alchemyFields.getInteger("Repos_PosY") in 3..5)
    }

    @Test
    fun storageAppendEditRemoveIsOrderedAndRoundTripsWithoutCollateralChanges(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val environment = loaded.environment
        val storage = requireNotNull(EquipmentAccess.storageList(loaded.session))
        val seed = storage.getElement(0).getValue() as DBList
        addTemplate(environment, seed, "test_storage_append", "Test storage item", quest = false, alchemy = false)
        val countBefore = storage.getElementCount()

        val controller = EditorCommandController(loaded.session)
        assertApplied(controller.dispatch(AddInventoryItemCommand(environment, InventoryDestination.STORAGE, "test_storage_append")))
        val appended = InventoryViewState.from(loaded.session).storage.last()
        assertEquals(countBefore.toString(), appended.id.removePrefix("storage-").substringBefore('-'))
        assertEquals("test_storage_append", appended.templateResRef)

        val edited = requireNotNull(readInventoryEditValues(loaded.session, appended.id)).copy(customCost = 777)
        assertApplied(controller.dispatch(EditInventoryItemCommand(environment, appended.id, edited)))
        assertEquals(777, EquipmentAccess.record(loaded.session, appended.id)!!.fields.getInteger("CustomCost"))
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val allowed = setOf(loaded.modName!!, loaded.smmName!!, "player.utc")
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), allowed)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedAdded = InventoryViewState.from(reloaded.session).storage.last { it.templateResRef == "test_storage_append" }
        assertEquals(777, EquipmentAccess.record(reloaded.session, reloadedAdded.id)!!.fields.getInteger("CustomCost"))

        val removeController = EditorCommandController(reloaded.session)
        assertApplied(removeController.dispatch(RemoveInventoryItemCommand(reloadedAdded.id)))
        assertTrue(removeController.apply().completed)
        val beforeRemoval = SaveSeamSupport.entryDigests(reloaded.saveDatabase!!)
        SaveSeamSupport.save(reloaded)

        val repackedAfterRemoval = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repackedAfterRemoval.load()
        SaveSeamSupport.assertUntouchedEntries(beforeRemoval, SaveSeamSupport.entryDigests(repackedAfterRemoval), allowed)
        val removed = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        assertFalse(InventoryViewState.from(removed.session).storage.any { it.templateResRef == "test_storage_append" })
    }

    @Test
    fun fullCarriedCellRejectsAnAdditionalItemWithoutMutatingTheSave(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val environment = loaded.environment
        val source = loaded.player!!.getElement("ItemList")!!.getValue() as DBList
        val seed = source.getElement(0).getValue() as DBList
        addTemplate(environment, seed, "test_full_satchel", "Full satchel item", quest = false, alchemy = false)

        for (index in 0 until 42) {
            val fields = seed.clone()
            fields.setString("TemplateResRef", "existing_satchel_$index")
            fields.setString("LocalizedName", "Existing satchel $index")
            fields.setInteger("QuestItem", 0)
            fields.setInteger("AlchIngredient", 0)
            fields.setInteger("Repos_PosX", index % 14, DBElement.WORD)
            fields.setInteger("Repos_PosY", index / 14, DBElement.WORD)
            source.addElement(DBElement(DBElement.STRUCT, 0, "", fields))
        }

        val beforeCount = source.getElementCount()
        val result = EditorCommandController(loaded.session).dispatch(
            AddInventoryItemCommand(environment, InventoryDestination.SATCHEL, "test_full_satchel")
        )
        assertTrue(result is EditorCommandResult.Rejected)
        assertEquals(beforeCount, source.getElementCount())
        assertFalse(loaded.session.isDataModified())
    }

    private fun addTemplate(
        environment: AppEnvironment,
        seed: DBList,
        resourceName: String,
        name: String,
        quest: Boolean,
        alchemy: Boolean,
        baseItem: Int = 0
    ) {
        val fields = seed.clone()
        fields.setString("TemplateResRef", resourceName)
        fields.setString("LocalizedName", name)
        fields.setInteger("BaseItem", baseItem)
        fields.setInteger("QuestItem", if (quest) 1 else 0)
        fields.setInteger("AlchIngredient", if (alchemy) 1 else 0)
        fields.setInteger("MaxStack", 1)
        environment.itemTemplates.add(ItemTemplate(fields))
    }

    private fun assertApplied(result: EditorCommandResult) {
        assertTrue(result is EditorCommandResult.Applied, (result as? EditorCommandResult.Rejected)?.problems?.joinToString())
    }
}
