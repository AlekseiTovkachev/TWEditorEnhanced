package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * The innkeeper storage chest lives in the save's meta database as
 * `smm/StoreList/<record IsStorage=1>/ItemList`. These tests prove the panel's
 * add/remove edits survive a write/reload round trip through the real save
 * archive, with every untouched entry byte-identical. Gated on a local save
 * that actually used storage (the tutorial fixture never opened the chest).
 */
@Timeout(300)
class StorageEditTest {
    private val environment: AppEnvironment by lazy {
        SaveSeamSupport.createEnvironment()
    }

    private fun storageSave(): File? {
        return SaveSeamSupport.localSaves().firstOrNull { save ->
            val probeEnvironment = SaveSeamSupport.createEnvironment()
            val loaded = SaveSeamSupport.load(probeEnvironment, save, Files.createTempDirectory("storage-test"))
            StoragePanel.findStorageRecord(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList) != null
        }
    }

    private fun storageSaveCopy(): File? {
        return storageSave()?.let { SaveSeamSupport.tempCopy(it) }
    }

    @Test
    fun storageItemsAreReadFromTheSmm(@TempDir tempDir: Path) {
        val save = storageSaveCopy() ?: return assumeTrue(false, "no local save with a used storage chest")
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val smmList = loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        val record = StoragePanel.findStorageRecord(smmList)
        assumeTrue(record != null, "no storage record")

        val itemList = record!!.getElement("ItemList")!!.getValue() as DBList
        val names = ArrayList<String>()
        for (itemElement in itemList) {
            val fields = itemElement.getValue() as DBList
            names.add(fields.getString("TemplateResRef") + " x" + fields.getInteger("StackSize"))
        }
        assertTrue(names.isNotEmpty(), "the owner's save must show the stored items")
    }

    @Test
    fun sortingTheChestPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = storageSaveCopy() ?: return assumeTrue(false, "no local save with a used storage chest")
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = StoragePanel(loaded.session, environment)
        panel.setFields(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        val structsBefore = chestStructs(loaded)
        val namesBefore = itemsList(panel).map { it.name }
        assumeTrue(namesBefore.size >= 2, "storage chest needs at least two items to sort")

        panel.sortChest()
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the meta database, module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val structsAfter = chestStructs(reloaded)
        val expected = structsBefore.sortedWith(
            compareBy({ panel.categoryOf(it) },
                { (it.getValue() as DBList).getString("LocalizedName").ifEmpty { (it.getValue() as DBList).getString("TemplateResRef") } },
                { (it.getValue() as DBList).getString("TemplateResRef") },
                { (it.getValue() as DBList).getInteger("StackSize") })
        )
        assertEquals(resrefSequence(expected), resrefSequence(structsAfter),
            "the chest must be stored in type-then-name order after the round trip")
        assertEquals(resrefSequence(structsBefore).sorted(), resrefSequence(structsAfter).sorted(),
            "sorting must not add or lose items")
    }

    private fun chestStructs(loaded: SaveSeamSupport.Loaded): List<DBElement> {
        val record = StoragePanel.findStorageRecord(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)!!
        return (record.getElement("ItemList")!!.getValue() as DBList).toList()
    }

    private fun resrefSequence(structs: List<DBElement>): List<String> {
        return structs.map { (it.getValue() as DBList).getString("TemplateResRef") + "#" + (it.getValue() as DBList).getInteger("StackSize") }
    }

    @Test
    fun addedAndRemovedStorageItemsPersistThroughRoundTrip(@TempDir tempDir: Path) {
        val save = storageSaveCopy() ?: return assumeTrue(false, "no local save with a used storage chest")
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = StoragePanel(loaded.session, environment)
        val smmList = loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        panel.setFields(smmList)
        val countBefore = panel.storageItemCount()
        assumeTrue(countBefore >= 2, "storage chest needs at least two items for the add+remove test")

        // add: clone an existing stored item struct (keeps the test independent
        // of the game install's template scan)
        val record = StoragePanel.findStorageRecord(smmList)!!
        val itemList = record.getElement("ItemList")!!.getValue() as DBList
        val firstStruct = itemList.getElement(0).getValue() as DBList
        val template = ItemTemplate(firstStruct)
        panel.addTemplate(template)

        // remove the second stored item
        val removed = itemsList(panel)[1]
        val removedName = removed.name
        val sameNameBefore = itemsList(panel).count { it.name == removedName }
        panel.removeItem(removed)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the .smm meta database, the module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedPanel = StoragePanel(reloaded.session, reloaded.environment)
        reloadedPanel.setFields(reloaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        assertEquals(countBefore, reloadedPanel.storageItemCount(),
            "one added item minus one removed item must keep the chest count stable")

        val reloadedNames = itemsList(reloadedPanel).map { it.name }
        assertTrue(reloadedNames.contains(template.itemName),
            "the added '" + template.itemName + "' must survive the write/reload round trip")
        val sameNameAfter = reloadedNames.count { it == removedName }
        assertEquals(sameNameBefore - 1, sameNameAfter,
            "exactly one instance of the removed '" + removedName + "' must be gone from the chest")
    }

    @Test
    fun removingOneOfSeveralCopiesKeepsTheRestRemovable(@TempDir tempDir: Path) {
        // The owner's repro: add 3 axes, remove 1, then the second removal
        // must also work (copies compare equal, so removal must be by identity).
        val save = storageSaveCopy() ?: return assumeTrue(false, "no local save with a used storage chest")
        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = StoragePanel(loaded.session, environment)
        panel.setFields(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        val axeStruct = chestStructs(loaded).map { it.getValue() as DBList }
            .firstOrNull { it.getString("TemplateResRef") == "it_laxe_001" }
            ?: return assumeTrue(false, "the chest has no axe struct to clone")
        val axeTemplate = ItemTemplate(axeStruct)
        val countBefore = panel.storageItemCount()

        panel.addTemplate(axeTemplate)
        panel.addTemplate(axeTemplate)
        panel.addTemplate(axeTemplate)
        assertEquals(countBefore + 3, panel.storageItemCount(), "three axes added")

        val firstRemoval = itemsList(panel).filter { it.name == axeTemplate.itemName }[1]
        panel.removeItem(firstRemoval)
        assertEquals(countBefore + 2, panel.storageItemCount(), "one axe removed")

        val secondRemoval = itemsList(panel).filter { it.name == axeTemplate.itemName }[1]
        panel.removeItem(secondRemoval)
        assertEquals(countBefore + 1, panel.storageItemCount(),
            "the second removal must work instead of reporting the item gone")
        SaveSeamSupport.save(loaded)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedPanel = StoragePanel(reloaded.session, reloaded.environment)
        reloadedPanel.setFields(reloaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        assertEquals(countBefore + 1, reloadedPanel.storageItemCount(),
            "exactly one of the three added axes must remain after the round trip")
    }

    private fun itemsList(panel: StoragePanel): List<InventoryItem> {
        val field = StoragePanel::class.java.getDeclaredField("itemsField")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(panel) as javax.swing.JList<InventoryItem>
        return (0 until list.model.size).map { list.model.getElementAt(it) }
    }
}
