package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files

class ItemDetailsTest {
    private lateinit var environment: AppEnvironment

    @BeforeEach
    fun setUp() {
        environment = SaveSeamSupport.createEnvironment()
    }

    @Test
    fun equippedItemDetailsRestoreDescriptionExtraDescriptionAndWeaponEffects(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val item = InventoryViewState.from(loaded.session).equipment.flatMap { it.items }.first()
        val fields = requireNotNull(EquipmentAccess.record(loaded.session, item.id)).fields
        fields.setString("DescIdentified", "<cBOLD>A sharp axe.</c>\nHandle with care.")
        fields.setString("ExtraDesc", "<cItalic>Causes bleeding.</c>")
        ItemEdit(environment, fields).setWeaponAbilities(
            listOf(WeaponAbility("focus_self", 2)),
            listOf(WeaponAbility("bleeding_on_hit", 1))
        )

        val details = requireNotNull(readItemDetails(environment, loaded.session, item.id))

        assertEquals("A sharp axe.\nHandle with care.\n\nCauses bleeding.", details.description)
        assertEquals(
            listOf(
                ItemEffectView("wielder", "focus_self", 2),
                ItemEffectView("onHit", "bleeding_on_hit", 1)
            ),
            details.effects
        )
        assertTrue(details.hasContent)
    }

    @Test
    fun detailsFallBackToTheMatchingTemplateWhenTheSaveRecordHasNoDescription(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val item = InventoryViewState.from(loaded.session).equipment.flatMap { it.items }.first()
        val source = requireNotNull(EquipmentAccess.record(loaded.session, item.id)).fields
        source.setString("DescIdentified", "")
        source.setString("Description", "")
        source.setString("ExtraDesc", "")
        val templateFields = source.clone().also {
            it.setString("TemplateResRef", item.templateResRef)
            it.setString("DescIdentified", "Template description")
            it.setString("ExtraDesc", "Template effect text")
        }
        environment.itemTemplates.add(ItemTemplate(templateFields))

        val details = requireNotNull(readItemDetails(environment, loaded.session, item.id))

        assertEquals("Template description\n\nTemplate effect text", details.description)
    }

    @Test
    fun gameMarkupBecomesReadablePlainText() {
        val text = ItemDescriptionText.toPlainText(
            "<cBOLD>Heading</c><br><ul><li>First</li><li><strref:42></li></ul>",
            resolveStringRef = { ref -> if (ref == 42) "Second" else "" }
        )

        assertEquals("Heading\n• First\n• Second", text)
    }

    @Test
    fun alchemyIngredientSubstancesAreExposedAsEffects(@TempDir tempDir: Path) {
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT),
            tempDir
        )
        val item = InventoryViewState.from(loaded.session).equipment.flatMap { it.items }.first()
        val fields = requireNotNull(EquipmentAccess.record(loaded.session, item.id)).fields
        fields.setInteger("AlchIngredient", 1, DBElement.DWORD)
        val table = tempDir.resolve("alchemy_ingre.2da")
        Files.writeString(
            table,
            "2DA V2.0\n\nNameRef Vitriol Rebis Aether Quebirth Hydragenum Vermilion Albedo Nigredo Rubedo\n" +
                "0 0 0 0 0 0 0 0 0 0 0\n" +
                "1 1 1 0 1 0 0 0 0 0 0\n"
        )
        environment.resourceFiles["alchemy_ingre.2da"] = table.toFile()

        val details = requireNotNull(readItemDetails(environment, loaded.session, item.id))

        assertEquals(
            listOf(ItemEffectView("alchemy", "Vitriol"), ItemEffectView("alchemy", "Aether")),
            details.effects.filter { it.groupKey == "alchemy" }
        )
    }
}
