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
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * The available-item trees of the Inventory/Storage/Equipment tabs, built with
 * the app's real template scan against the local game install, plus the real
 * add paths through the panel methods. Guards the "the item list is empty"
 * class of bug: weapons appear only in Storage and Equipment, never in the
 * Inventory tree; armor and potions exist where the game allows them.
 */
@Timeout(600)
class PanelTreesTest {
    private fun gameEnvironment(): AppEnvironment? {
        val installData = "C:\\Games\\The Witcher Enhanced Edition\\Data"
        val mainKey = File(installData, "main.key")
        if (!mainKey.exists()) {
            return null
        }
        val environment = AppEnvironment()
        environment.fileSeparator = "\\"
        environment.languageID = 3
        environment.stringsDatabase = StringsDatabase(File(installData, "dialog_3.tlk"))
        environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))
        return environment
    }

    private fun template(environment: AppEnvironment, resref: String): ItemTemplate {
        return environment.itemTemplates.first { it.fieldList.getString("TemplateResRef") == resref }
    }

    private fun childCount(tree: JTree, category: String): Int {
        val root = tree.getModel().getRoot() as DefaultMutableTreeNode
        for (i in 0 until root.childCount) {
            val node = root.getChildAt(i) as DefaultMutableTreeNode
            if (node.userObject == category) {
                return node.childCount
            }
        }
        return -1
    }

    private fun childBaseItems(tree: JTree, category: String): Set<Int> {
        val root = tree.getModel().getRoot() as DefaultMutableTreeNode
        for (i in 0 until root.childCount) {
            val node = root.getChildAt(i) as DefaultMutableTreeNode
            if (node.userObject == category) {
                val baseItems = HashSet<Int>()
                for (j in 0 until node.childCount) {
                    val template = (node.getChildAt(j) as DefaultMutableTreeNode).userObject as ItemTemplate
                    baseItems.add(template.baseItem)
                }
                return baseItems
            }
        }
        return emptySet()
    }

    @Test
    fun theAvailableTreesPopulateFromTheRealTemplateScan(@TempDir tempDir: Path) {
        val environment = gameEnvironment()
            ?: return assumeTrue(false, "no local game install at C:\\Games\\The Witcher Enhanced Edition")
        LoadTemplates.loadItemTemplates(environment)
        assumeTrue(environment.itemTemplates.size > 900, "the template scan must find the game's .uti templates")

        val save = SaveSeamSupport.localSaves().firstOrNull { it.name.startsWith("000030") }
            ?: return assumeTrue(false, "no local save 30 with equipment")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val inventory = InventoryPanel(loaded.session, environment)
        inventory.setFields(loaded.player!!)
        assertEquals(-1, childCount(inventory.availTree(), "Weapon"), "inventory does not hold weapons")
        assertTrue(childCount(inventory.availTree(), "Potion") > 0, "inventory potions")
        assertTrue(childCount(inventory.availTree(), "Other") > 0, "inventory other")
        assertTrue(childBaseItems(inventory.availTree(), "Other").contains(27), "inventory holds keys")

        val storage = StoragePanel(loaded.session, environment)
        storage.setFields(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        assertTrue(childCount(storage.availTree(), "Steel Sword") > 0, "storage steel swords")
        assertTrue(childCount(storage.availTree(), "Silver Sword") > 0, "storage silver swords")
        assertTrue(childCount(storage.availTree(), "Big Weapon") > 0, "storage big weapons")
        assertTrue(childCount(storage.availTree(), "Short Weapon") > 0, "storage short weapons")
        assertTrue(childCount(storage.availTree(), "Armor") > 0, "storage holds armor")
        assertTrue(childCount(storage.availTree(), "Potion") > 0, "storage potions")
        assertTrue(childCount(storage.availTree(), "Other") > 0, "storage catch-all")
        assertEquals(-1, childCount(storage.availTree(), "Weapon"),
            "the old combined Weapon tab is replaced by the four weapon kinds")

        val equip = EquipPanel(loaded.session, environment)
        equip.setFields(loaded.player!!)
        assertTrue(childCount(equip.availTree(), "Steel Sword") > 0, "equip steel swords")
        assertTrue(childCount(equip.availTree(), "Short Weapon") > 0, "equip short weapons")
        assertTrue(childCount(equip.availTree(), "Potion") > 0, "equip potions for elixir slots")
        assertTrue(childCount(equip.availTree(), "Accessory") > 0, "equip rings")
    }

    @Test
    fun aRealArmorTemplateStoresThroughThePanelAndRoundTrips(@TempDir tempDir: Path) {
        val environment = gameEnvironment()
            ?: return assumeTrue(false, "no local game install at C:\\Games\\The Witcher Enhanced Edition")
        LoadTemplates.loadItemTemplates(environment)
        val original = SaveSeamSupport.localSaves().firstOrNull { it.name.startsWith("000029") }
            ?: return assumeTrue(false, "no local save 29 with a used storage chest")
        val save = SaveSeamSupport.tempCopy(original)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = StoragePanel(loaded.session, environment)
        panel.setFields(loaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        val countBefore = panel.storageItemCount()
        assumeTrue(countBefore > 0, "save 29 must have a used storage chest")

        panel.addTemplate(template(environment, "it_witcharm_002"))
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the meta database, module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedPanel = StoragePanel(reloaded.session, reloaded.environment)
        reloadedPanel.setFields(reloaded.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList)
        assertEquals(countBefore + 1, reloadedPanel.storageItemCount(),
            "the stored armor must survive the write/reload round trip")
        assertTrue(itemsOf(reloadedPanel).any { it.name == template(environment, "it_witcharm_002").itemName },
            "the stored '" + template(environment, "it_witcharm_002").itemName + "' must be in the chest")
    }

    @Test
    fun aRealSilverSwordEquipsThroughThePanelAndRoundTrips(@TempDir tempDir: Path) {
        val environment = gameEnvironment()
            ?: return assumeTrue(false, "no local game install at C:\\Games\\The Witcher Enhanced Edition")
        LoadTemplates.loadItemTemplates(environment)
        val original = SaveSeamSupport.localSaves().firstOrNull { it.name.startsWith("000030") }
            ?: return assumeTrue(false, "no local save 30 with equipment")
        val save = SaveSeamSupport.tempCopy(original)
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = EquipPanel(loaded.session, environment)
        panel.setFields(loaded.player!!)
        val countBefore = equipCount(loaded)

        panel.addTemplateToSlot(template(environment, "it_svswd_001"), WeaponSlots.BACK_SILVER)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the meta database, module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val equipList = reloaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        assertEquals(countBefore + 1, equipList.getElementCount(), "the added sword must persist")
        val silverSlots = equipList.map { (it.getValue() as DBList) }
            .filter { it.getString("TemplateResRef") == "it_svswd_001" }
            .map { it.getInteger("WeaponSlot") }
        assertTrue(silverSlots.contains(WeaponSlots.BACK_SILVER),
            "the silver sword must be in the back slot, got " + silverSlots)
    }

    private fun itemsOf(panel: StoragePanel): List<InventoryItem> {
        val field = StoragePanel::class.java.getDeclaredField("itemsField")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(panel) as javax.swing.JList<InventoryItem>
        return (0 until list.model.size).map { list.model.getElementAt(it) }
    }

    private fun equipCount(loaded: SaveSeamSupport.Loaded): Int {
        val equipList = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        return equipList.getElementCount()
    }
}
