package app.tweditor

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EquipmentCommandTest {
    @Test
    fun pickerOmitsKnownNonEquipmentTemplates(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val environment = loaded.environment
        val source = (loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList)
            .first { it.getValue() is DBList }.getValue() as DBList
        val compatible = source.clone().also {
            it.setString("LocalizedName", "Compatible elixir")
            it.setString("TemplateResRef", "test_compatible_elixir")
            it.setInteger("BaseItem", 22)
        }
        val ordinary = source.clone().also {
            it.setString("LocalizedName", "Ordinary item")
            it.setString("TemplateResRef", "test_ordinary_item")
            it.setInteger("BaseItem", 24)
        }
        environment.itemTemplates.clear()
        environment.itemTemplates.add(ItemTemplate(compatible))
        environment.itemTemplates.add(ItemTemplate(ordinary))
        installMasks(environment, tempDir, mapOf(22 to 0x200000, 24 to 0))

        val picker = EquipmentCatalog.pickerItems(environment, WeaponSlots.ELIXIR_1)

        assertTrue(picker.any { it.resourceName == "test_compatible_elixir" })
        assertFalse(picker.any { it.resourceName == "test_ordinary_item" })
    }

    @Test
    fun repeatingAStaleEquipmentRemovalCannotRemoveTheNextRecord(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val equipment = requireNotNull(EquipmentAccess.equipmentList(loaded.session, create = false))
        val item = InventoryViewState.from(loaded.session).equipment
            .flatMap { it.items }
            .first { viewItem ->
                val index = viewItem.id.substringAfter('-').substringBefore('-').toInt()
                index < equipment.getElementCount() - 1
            }
        val controller = EditorCommandController(loaded.session)

        assertTrue(controller.dispatch(RemoveEquipmentCommand(item.id)) is EditorCommandResult.Applied)
        val countAfterFirstRemoval = equipment.getElementCount()
        val repeated = controller.dispatch(RemoveEquipmentCommand(item.id))

        assertTrue(repeated is EditorCommandResult.Rejected)
        assertEquals(countAfterFirstRemoval, equipment.getElementCount())
        assertEquals(1, controller.state().pendingChanges.size)
    }

    @Test
    fun knownIllegalDropIsRejectedWithoutChangingEitherSide(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val source = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val daggerIndex = source.withIndex().first { it.value.getValue() is DBList && (it.value.getValue() as DBList).getInteger("BaseItem") == 17 }.index
        val daggerId = "equipment-$daggerIndex"
        val environment = loaded.environment
        installMasks(environment, tempDir, mapOf(17 to 0x30010))

        val dagger = source.getElement(daggerIndex).getValue() as DBList
        val beforeSlot = dagger.getInteger("WeaponSlot")
        val beforeCount = source.getElementCount()
        val controller = EditorCommandController(loaded.session)
        val result = controller.dispatch(
            EquipItemCommand(environment, daggerId, WeaponSlots.BACK_SILVER, confirmUncertain = true)
        )

        assertTrue(result is EditorCommandResult.Rejected)
        assertEquals(
            "equipment.compat.cannotGo",
            (result as EditorCommandResult.Rejected).problems.single().key
        )
        assertEquals(beforeSlot, dagger.getInteger("WeaponSlot"))
        assertEquals(beforeCount, source.getElementCount())
        assertFalse(loaded.session.isDataModified())
    }

    @Test
    fun equippingCarriedItemIsUndoableAndPersistsThroughSaveReload(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val environment = loaded.environment
        val carried = loaded.player!!.getElement("ItemList")!!.getValue() as DBList
        val sourceIndex = carried.withIndex().first { (_, element) ->
            val fields = element.getValue() as DBList
            fields.getInteger("BaseItem") > 0
        }.index
        val sourceFields = carried.getElement(sourceIndex).getValue() as DBList
        val sourceBaseItem = sourceFields.getInteger("BaseItem")
        installMasks(environment, tempDir, mapOf(sourceBaseItem to 0x4000))

        val equipment = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val equipmentBefore = equipment.getElementCount()
        val carriedBefore = carried.getElementCount()
        val sourceId = "inventory-$sourceIndex"
        val controller = EditorCommandController(loaded.session)
        val applied = controller.dispatch(
            EquipItemCommand(environment, sourceId, WeaponSlots.BACK_SILVER)
        )
        assertTrue(applied is EditorCommandResult.Applied, (applied as? EditorCommandResult.Rejected)?.problems?.joinToString())
        assertEquals(carriedBefore - 1, carried.getElementCount())
        assertEquals(equipmentBefore + 1, equipment.getElementCount())
        assertEquals(WeaponSlots.BACK_SILVER, sourceFields.getInteger("WeaponSlot"))
        assertEquals(0, controller.undo().problems.size)
        assertEquals(equipmentBefore, EquipmentAccess.equipmentList(loaded.session, create = false)!!.getElementCount())
        val restoredSource = EquipmentAccess.record(loaded.session, sourceId)!!
        assertEquals(EquipmentRecordSource.INVENTORY, restoredSource.source)
        assertEquals(carriedBefore, restoredSource.list.getElementCount(), "undo restores the source list")

        val appliedAgain = controller.dispatch(
            EquipItemCommand(environment, sourceId, WeaponSlots.BACK_SILVER)
        )
        assertTrue(appliedAgain is EditorCommandResult.Applied)
        controller.apply()
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), setOf(loaded.modName!!))

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedCarried = reloaded.player!!.getElement("ItemList")!!.getValue() as DBList
        val reloadedEquipment = reloaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        assertEquals(carriedBefore - 1, reloadedCarried.getElementCount())
        assertTrue(reloadedEquipment.any { element ->
            val fields = element.getValue() as DBList
            fields.getInteger("BaseItem") == sourceBaseItem && fields.getInteger("WeaponSlot") == WeaponSlots.BACK_SILVER
        })
    }

    @Test
    fun equippingStorageItemTransfersBothArchiveSidesThroughSaveReload(@TempDir tempDir: Path) {
        val loaded = loadStorageFixture(tempDir)
        val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
        val environment = loaded.environment
        val storage = requireNotNull(EquipmentAccess.storageList(loaded.session))
        val sourceIndex = storage.withIndex().first { (_, element) ->
            (element.getValue() as DBList).getInteger("BaseItem") > 0
        }.index
        val sourceFields = storage.getElement(sourceIndex).getValue() as DBList
        val sourceBaseItem = sourceFields.getInteger("BaseItem")
        installMasks(environment, tempDir, mapOf(sourceBaseItem to 0x4000))
        val storageBefore = storage.getElementCount()

        val controller = EditorCommandController(loaded.session)
        val result = controller.dispatch(
            EquipItemCommand(
                environment = environment,
                sourceId = "storage-$sourceIndex",
                targetSlot = WeaponSlots.BACK_SILVER,
                replaceExisting = true
            )
        )
        assertTrue(result is EditorCommandResult.Applied, (result as? EditorCommandResult.Rejected)?.problems?.joinToString())
        assertEquals(storageBefore - 1, EquipmentAccess.storageList(loaded.session)!!.getElementCount())
        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), loaded.saveDatabase!!.getFile())
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), setOf(loaded.modName!!, loaded.smmName!!))
        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), loaded.saveDatabase!!.getFile(), tempDir)
        assertEquals(storageBefore - 1, EquipmentAccess.storageList(reloaded.session)!!.getElementCount())
        val equipped = reloaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        assertTrue(equipped.any { element ->
            val fields = element.getValue() as DBList
            fields.getInteger("BaseItem") == sourceBaseItem && fields.getInteger("WeaponSlot") == WeaponSlots.BACK_SILVER
        })
    }

    @Test
    fun templateAddReplaceEditAndRemoveUseTheExactPaperdollDestinations(@TempDir tempDir: Path) {
        val loaded = loadFixture(tempDir)
        val environment = loaded.environment
        val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
        val equipment = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val existing = equipment.getElement(0).getValue() as DBList
        val baseItem = existing.getInteger("BaseItem")
        installMasks(environment, tempDir, mapOf(baseItem to 0x40000))
        val templateFields = existing.clone()
        templateFields.setString("LocalizedName", "Test replacement")
        templateFields.setString("TemplateResRef", "test_replacement")
        templateFields.setInteger("MaxStack", 1)
        environment.itemTemplates.add(ItemTemplate(templateFields))

        val controller = EditorCommandController(loaded.session)
        val target = WeaponSlots.BIG_WEAPON
        val add = controller.dispatch(
            EquipTemplateCommand(environment, "test_replacement", target, replaceExisting = true)
        )
        assertTrue(add is EditorCommandResult.Applied, (add as? EditorCommandResult.Rejected)?.problems?.joinToString())
        assertTrue(equipment.any { (it.getValue() as DBList).getString("TemplateResRef") == "test_replacement" })

        val addedId = InventoryViewState.from(loaded.session).equipment.first { it.slot == target }.item!!.id
        val editValues = requireNotNull(readEquipmentEditValues(loaded.session, addedId)).copy(customCost = 777)
        assertTrue(controller.dispatch(EditEquipmentCommand(environment, addedId, editValues)) is EditorCommandResult.Applied)
        val afterEdit = EquipmentAccess.record(loaded.session, addedId)!!
        assertEquals(777, afterEdit.fields.getInteger("CustomCost"))

        assertTrue(controller.apply().completed)
        SaveSeamSupport.save(loaded)
        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), loaded.saveDatabase!!.getFile())
        repacked.load()
        SaveSeamSupport.assertUntouchedEntries(before, SaveSeamSupport.entryDigests(repacked), setOf(loaded.modName!!))

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), loaded.saveDatabase!!.getFile(), tempDir)
        val reloadedView = InventoryViewState.from(reloaded.session)
        val reloadedAddedId = reloadedView.equipment.first { it.slot == target }.item!!.id
        assertEquals("test_replacement", EquipmentAccess.record(reloaded.session, reloadedAddedId)!!.fields.getString("TemplateResRef"))
        assertEquals(777, EquipmentAccess.record(reloaded.session, reloadedAddedId)!!.fields.getInteger("CustomCost"))

        val reloadedController = EditorCommandController(reloaded.session)
        assertTrue(reloadedController.dispatch(RemoveEquipmentCommand(reloadedAddedId)) is EditorCommandResult.Applied)
        assertTrue(reloadedController.apply().completed)
        val beforeRemoval = SaveSeamSupport.entryDigests(reloaded.saveDatabase!!)
        SaveSeamSupport.save(reloaded)
        val repackedAfterRemoval = SaveDatabase(SaveSeamSupport.createEnvironment(), reloaded.saveDatabase!!.getFile())
        repackedAfterRemoval.load()
        SaveSeamSupport.assertUntouchedEntries(beforeRemoval, SaveSeamSupport.entryDigests(repackedAfterRemoval), setOf(reloaded.modName!!))
        val removed = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), reloaded.saveDatabase!!.getFile(), tempDir)
        assertTrue(InventoryViewState.from(removed.session).equipment.none { destination ->
            destination.items.any { it.templateResRef == "test_replacement" }
        })
    }

    private fun loadFixture(tempDir: Path): SaveSeamSupport.Loaded =
        SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )

    private fun loadStorageFixture(tempDir: Path): SaveSeamSupport.Loaded =
        SaveSeamSupport.load(
            SaveSeamSupport.createEnvironment(),
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE),
            tempDir
        )

    private fun installMasks(environment: AppEnvironment, tempDir: Path, overrides: Map<Int, Int>) {
        val max = maxOf(32, overrides.keys.maxOrNull() ?: 0)
        val text = buildString {
            appendLine("2DA V2.0")
            appendLine()
            appendLine("EquipableSlots")
            for (index in 0..max) {
                appendLine("$index 0x${overrides[index]?.toString(16) ?: "0"}")
            }
        }
        val file = tempDir.resolve("baseitems.2da")
        Files.writeString(file, text)
        environment.resourceFiles["baseitems.2da"] = file.toFile()
    }
}
