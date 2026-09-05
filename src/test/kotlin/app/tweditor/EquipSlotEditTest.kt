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
 * Equipment slots: each equipped item struct carries `WeaponSlot`, the
 * weaponslots.2da row of the position it occupies (steel sword can sit on the
 * back or in the big-weapon position; short weapons have two sidearm slots;
 * elixirs have three). These tests prove a slot move and an added equipped
 * item survive a write/reload round trip through the real save archive, with
 * every untouched entry byte-identical. Gated on the local save with weapons
 * in equipment (save 30).
 */
@Timeout(300)
class EquipSlotEditTest {
    private fun save30(): File? {
        val original = SaveSeamSupport.localSaves().firstOrNull { it.name.startsWith("000030") }
            ?: return null
        return SaveSeamSupport.tempCopy(original)
    }

    @Test
    fun theSteelMaskAllowsTheTwoSwordPositionsAndTheSidearmSlots() {
        // steel: back, big weapon, left hand (the 0x10 bit has no slot row)
        assertEquals(listOf(1, 10, 26), WeaponSlots.slotsFor(0x48030), "steel sword mask")
        assertEquals(listOf(2, 26), WeaponSlots.slotsFor(0x4030), "silver sword mask")
        assertEquals(listOf(7, 8, 9), WeaponSlots.slotsFor(0xE00000), "elixir mask")
        assertEquals(listOf(27), WeaponSlots.slotsFor(0x8000000), "armor mask")
        assertEquals(listOf(14, 16), WeaponSlots.slotsFor(0x88), "ring mask (forearms)")
        assertEquals(listOf(3, 4), WeaponSlots.slotsFor(0x30010), "short weapon mask")
    }

    @Test
    fun movingAnItemToAnotherSlotPersistsThroughRoundTrip(@TempDir tempDir: Path) {
        val save = save30() ?: return assumeTrue(false, "no local save 30 with weapons in equipment")
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val equipList = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val dagger = equipList.map { it.getValue() as DBList }.first { it.getInteger("BaseItem") == 17 }
        val fromSlot = dagger.getInteger("WeaponSlot")
        val toSlot = if (fromSlot == WeaponSlots.SHORT_1) WeaponSlots.SHORT_2 else WeaponSlots.SHORT_1

        dagger.getElement("WeaponSlot")!!.setValue(toSlot)
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the meta database, module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedEquip = reloaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val reloadedDagger = reloadedEquip.map { it.getValue() as DBList }.first { it.getInteger("BaseItem") == 17 }
        assertEquals(toSlot, reloadedDagger.getInteger("WeaponSlot"),
            "the moved short weapon must keep its new slot after the write/reload round trip")
    }

    @Test
    fun addingAEquippedItemToTheSecondSwordPositionPersists(@TempDir tempDir: Path) {
        val save = save30() ?: return assumeTrue(false, "no local save 30 with weapons in equipment")
        val pristine = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        pristine.load()
        val before = SaveSeamSupport.entryDigests(pristine)

        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val equipList = loaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        val countBefore = equipList.getElementCount()
        val steel = equipList.map { it.getValue() as DBList }.first { it.getInteger("BaseItem") == 1 }

        // The panel's add path: clone the item struct and give it the other
        // steel position (big weapon) once the back is taken.
        val fieldList = steel.clone()
        fieldList.setInteger("WeaponSlot", WeaponSlots.BIG_WEAPON)
        equipList.addElement(DBElement(14, 0, "", fieldList))
        SaveSeamSupport.save(loaded)

        val repacked = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.smmName!!, loaded.modName!!, "player.utc")
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries beyond the meta database, module container and player.utc changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        val reloadedEquip = reloaded.player!!.getElement("Equip_ItemList")!!.getValue() as DBList
        assertEquals(countBefore + 1, reloadedEquip.getElementCount(), "the added equipped item must persist")
        val steelSlots = reloadedEquip.map { (it.getValue() as DBList) }
            .filter { it.getInteger("BaseItem") == 1 }
            .map { it.getInteger("WeaponSlot") }
        assertTrue(steelSlots.contains(WeaponSlots.BACK_NORMAL) && steelSlots.contains(WeaponSlots.BIG_WEAPON),
            "the two steel swords must occupy the back and big-weapon positions, got " + steelSlots)
    }

    @Test
    fun thePaperdollShowsPopulatedAndEmptySlots(@TempDir tempDir: Path) {
        val save = save30() ?: return assumeTrue(false, "no local save 30 with weapons in equipment")
        val environment = SaveSeamSupport.createEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        val panel = EquipPanel(loaded.session, environment)
        panel.setFields(loaded.player!!)

        val model = slotRows(panel)
        assertEquals(12, model.size, "the paperdoll shows the game's 12 Geralt slots (Left_Hand is a leftover)")
        val steel = model.first { it.slot == WeaponSlots.BACK_NORMAL }
        assertTrue(steel.item != null, "steel sword populated")
        assertEquals("it_stlswd_001",
            (steel.item!!.element.getValue() as DBList).getString("TemplateResRef"),
            "the steel sword row holds the save's equipped sword (the save carries inline names)")
        assertTrue(model.first { it.slot == WeaponSlots.BACK_SILVER }.item == null, "silver sword empty in save 30")
        assertTrue(model.first { it.slot == WeaponSlots.SHORT_1 }.item != null, "dagger in short weapon 1")
        assertTrue(model.first { it.slot == WeaponSlots.BIG_WEAPON }.item != null, "axe in big weapon")
        assertTrue(model.first { it.slot == WeaponSlots.ARMOR }.item != null, "armor populated")
        assertTrue(model.first { it.slot == WeaponSlots.ELIXIR_1 }.item == null, "elixir slots empty")
        assertTrue(model.first { it.slot == WeaponSlots.FOREARM_RIGHT }.item == null, "ring slots empty")
    }

    private fun slotRows(panel: EquipPanel): List<SlotEntry> {
        val field = EquipPanel::class.java.getDeclaredField("slotsField")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(panel) as javax.swing.JList<SlotEntry>
        return (0 until list.model.size).map { list.model.getElementAt(it) }
    }
}
