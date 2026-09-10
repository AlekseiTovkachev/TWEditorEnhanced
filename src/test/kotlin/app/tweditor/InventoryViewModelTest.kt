package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class InventoryViewModelTest {
    @Test
    fun acceptedLayoutHasOnlyTheTwelveGameDestinations() {
        assertEquals(
            listOf(
                "equipment.slot.steelSword", "equipment.slot.silverSword", "equipment.slot.shortWeapon1",
                "equipment.slot.shortWeapon2", "equipment.slot.bigWeapon", "equipment.slot.armor",
                "equipment.slot.trophy", "equipment.slot.ringRight", "equipment.slot.ringLeft",
                "equipment.slot.elixir1", "equipment.slot.elixir2", "equipment.slot.elixir3"
            ),
            InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.map { it.second }
        )
        val messages = EditorMessages.english()
        assertEquals(
            listOf(
                "Steel sword", "Silver sword", "Short weapon 1", "Short weapon 2", "Big weapon", "Armor",
                "Trophy", "Ring, right", "Ring, left", "Elixir 1", "Elixir 2", "Elixir 3"
            ),
            InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.map { messages.get(it.second) }
        )
        assertEquals(InventoryLayout.WIDE, inventoryLayoutFor(1440))
        assertEquals(InventoryLayout.NARROW, inventoryLayoutFor(900))
    }

    @Test
    fun fixtureInventoryIsImmutableAndRetainsTheTwoFixedGrids(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE),
            tempDir
        )
        val view = InventoryViewState.from(loaded.session)

        assertTrue(view.storage.isNotEmpty(), "the storage fixture must render its ordered storage records")
        assertEquals(42, view.satchel.cells.size)
        assertEquals(42, view.alchemy.cells.size)
        assertEquals(42, view.satchel.maxCells)
        assertEquals(42, view.alchemy.maxCells)
        assertEquals(12, view.equipment.size)
    }

    @Test
    fun unfamiliarRecordsAreShownAsWarningsInsteadOfBeingNormalized(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.STORAGE),
            tempDir
        )
        val smmTop = loaded.session.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        val record = StorageAccess.findStorageRecord(smmTop)!!
        val items = record.getElement("ItemList")!!.getValue() as DBList
        val unfamiliar = DBList(environment, 2)
        unfamiliar.setInteger("StackSize", 1, 2)
        unfamiliar.setInteger("BaseItem", 999)
        items.addElement(DBElement(14, 0, "", unfamiliar))

        val view = InventoryViewState.from(loaded.session)
        assertTrue(view.storage.any { it.name == "Unfamiliar record" && it.warning != null })
        assertTrue(view.warnings.any { it.contains("unfamiliar", ignoreCase = true) })
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
