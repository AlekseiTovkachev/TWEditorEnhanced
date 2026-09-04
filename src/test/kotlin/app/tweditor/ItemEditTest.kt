package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ItemEditTest {
    @Test
    fun fixtureWeaponStartsWithItsBaseAbility(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val edit = itemEdit(loaded)
        assertEquals(listOf(WeaponAbility("it_stlswd_006", 1)), edit.weaponAbilitiesSelf,
            "the rusty sword must carry its own base ability reference")
        assertEquals(emptyList<WeaponAbility>(), edit.weaponAbilitiesOpp)
        assertEquals(6, edit.modelPart1)
        assertEquals(100, edit.customCost)
        assertEquals("", edit.weaponType, "game-written items carry no WeaponType field")
    }

    @Test
    fun addedWeaponAbilitiesPersistThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val edit = itemEdit(loaded)
        val self = edit.weaponAbilitiesSelf + WeaponAbility("meteorite_red1_self", 1)
        edit.setWeaponAbilities(self, edit.weaponAbilitiesOpp + WeaponAbility("meteorite_red1_opp", 1))
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        val reloadedEdit = itemEdit(reloaded)
        assertEquals(listOf(WeaponAbility("it_stlswd_006", 1), WeaponAbility("meteorite_red1_self", 1)),
            reloadedEdit.weaponAbilitiesSelf, "added self ability must survive the write/reload round trip")
        assertEquals(listOf(WeaponAbility("meteorite_red1_opp", 1)), reloadedEdit.weaponAbilitiesOpp,
            "the opponent ability list must be created on an item that had no WpnAbilityOpp field")
        assertEquals(6, reloadedEdit.modelPart1, "ModelPart1 must not be touched by an ability edit")
        assertEquals(100, reloadedEdit.customCost, "CustomCost must not be touched by an ability edit")
    }

    @Test
    fun removedWeaponAbilitiesPersistThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        itemEdit(loaded).setWeaponAbilities(emptyList(), emptyList())
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        val reloadedEdit = itemEdit(reloaded)
        assertEquals(emptyList<WeaponAbility>(), reloadedEdit.weaponAbilitiesSelf,
            "removing all self abilities must survive the round trip")
        assertEquals(emptyList<WeaponAbility>(), reloadedEdit.weaponAbilitiesOpp)
        assertEquals(6, reloadedEdit.modelPart1)
        assertEquals(100, reloadedEdit.customCost)
    }

    @Test
    fun statFieldEditsPersistThroughRoundTrip(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val edit = itemEdit(loaded)
        edit.setModelPart1(2)
        edit.setCustomCost(750)
        edit.setQuality(2)
        edit.setWeaponType("WitcherSteelSword")
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        val reloadedEdit = itemEdit(reloaded)
        assertEquals(2, reloadedEdit.modelPart1, "ModelPart1 (appearance) must survive the round trip")
        assertEquals(750, reloadedEdit.customCost, "CustomCost (price) must survive the round trip")
        assertEquals(2, reloadedEdit.quality, "Quality must be added on an item that had no Quality field")
        assertEquals("WitcherSteelSword", reloadedEdit.weaponType,
            "WeaponType must be added on an item that had no WeaponType field")
        assertEquals(listOf(WeaponAbility("it_stlswd_006", 1)), reloadedEdit.weaponAbilitiesSelf,
            "stat edits must not touch the ability lists")
    }

    @Test
    fun copiedPowerFromMeteoriteTemplateReplacesAbilityLists(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        val meteoriteSword = DBList(environment, 8)
        meteoriteSword.addElement(DBElement(DBElement.STRING, 0, "Tag", "steelsword; RRR"))
        meteoriteSword.addElement(DBElement(DBElement.BYTE, 0, "Quality", 0))
        val selfList = DBList(environment, 3)
        for (name in arrayOf("meteorite_red1_self", "meteorite_red2_self", "meteorite_red3_self")) {
            selfList.addElement(abilityTemplateEntry(name))
        }
        meteoriteSword.addElement(DBElement(DBElement.LIST, 0, "WpnAbilitySelf", selfList))
        val oppList = DBList(environment, 3)
        for (name in arrayOf("meteorite_red1_opp", "meteorite_red2_opp", "meteorite_red3_opp")) {
            oppList.addElement(abilityTemplateEntry(name))
        }
        meteoriteSword.addElement(DBElement(DBElement.LIST, 0, "WpnAbilityOpp", oppList))

        itemEdit(loaded).copyWeaponPowerFrom(meteoriteSword)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        val reloadedEdit = itemEdit(reloaded)
        assertEquals(
            listOf(
                WeaponAbility("meteorite_red1_self", 0), WeaponAbility("meteorite_red2_self", 0),
                WeaponAbility("meteorite_red3_self", 0)
            ),
            reloadedEdit.weaponAbilitiesSelf, "the copied self abilities must survive the round trip"
        )
        assertEquals(
            listOf(
                WeaponAbility("meteorite_red1_opp", 0), WeaponAbility("meteorite_red2_opp", 0),
                WeaponAbility("meteorite_red3_opp", 0)
            ),
            reloadedEdit.weaponAbilitiesOpp, "the copied opponent abilities must survive the round trip"
        )
        assertEquals(6, reloadedEdit.modelPart1, "copying power must not change the model (appearance)")
        assertEquals(100, reloadedEdit.customCost, "copying power must not change the price")
    }

    @Test
    fun localSavesSurviveWeaponAbilityRewrites(@TempDir tempDir: Path) {
        val savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile()
        val saves = savesDir.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
        org.junit.jupiter.api.Assumptions.assumeTrue(saves != null && saves.isNotEmpty(),
            "no local saves in '" + savesDir + "'")

        for (save in saves) {
            val workDir = Files.createDirectory(tempDir.resolve("edit-" + save.name))
            val copy = Files.copy(save.toPath(), workDir.resolve(save.name)).toFile()
            val loaded = SaveSeamSupport.load(environment, copy, workDir)
            val edit = findWeaponEdit(loaded)
                ?: continue

            val pristine = SaveDatabase(environment, copy)
            pristine.load()
            val before = SaveSeamSupport.entryDigests(pristine)

            edit.setWeaponAbilities(edit.weaponAbilitiesSelf, edit.weaponAbilitiesOpp)
            SaveSeamSupport.save(loaded)

            val repacked = SaveDatabase(environment, copy)
            repacked.load()
            val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
            val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
            assertTrue(allowedToChange.containsAll(rewritten), save.name + ": unexpected entries changed: " + rewritten)

            val reloaded = SaveSeamSupport.load(environment, copy, workDir)
            val reloadedEdit = findWeaponEdit(reloaded)!!
            assertEquals(edit.weaponAbilitiesSelf, reloadedEdit.weaponAbilitiesSelf,
                save.name + ": the identical ability rewrite must not change the self list")
            assertEquals(edit.weaponAbilitiesOpp, reloadedEdit.weaponAbilitiesOpp,
                save.name + ": the identical ability rewrite must not change the opponent list")
        }
    }

    private fun itemEdit(loaded: SaveSeamSupport.Loaded): ItemEdit {
        val equipList = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        for (itemElement in equipList) {
            val fields = itemElement.getValue() as DBList
            if (fields.getInteger("BaseItem") == 1) {
                return ItemEdit(loaded.environment, fields)
            }
        }
        throw AssertionError("the fixture save has no steel sword in Equip_ItemList")
    }

    private fun findWeaponEdit(loaded: SaveSeamSupport.Loaded): ItemEdit? {
        val player = loaded.player!!
        for (label in arrayOf("Equip_ItemList", "ItemList")) {
            val element = player.getElement(label) ?: continue
            val itemList = element.getValue() as DBList
            for (itemElement in itemList) {
                val fields = itemElement.getValue() as DBList
                val edit = ItemEdit(loaded.environment, fields)
                if (edit.weaponAbilitiesSelf.isNotEmpty() || edit.weaponAbilitiesOpp.isNotEmpty()) {
                    return edit
                }
            }
        }
        return null
    }

    private fun abilityTemplateEntry(name: String): DBElement {
        val entryFields = DBList(environment, 1)
        entryFields.addElement(DBElement(DBElement.STRING, 0, "RnAbName", name))
        return DBElement(DBElement.STRUCT, 0, "", entryFields)
    }

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
