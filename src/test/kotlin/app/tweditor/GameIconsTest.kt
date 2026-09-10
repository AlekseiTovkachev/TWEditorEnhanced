package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Verifies the icon mapping against the real game install. Skips when the
 * install is not present (CI has none); on the owner's machine it proves every
 * displayed label and a sample of item templates resolve to decodable textures.
 */
@Timeout(120)
@Tag("local")
class GameIconsTest {

    @Test
    fun everySignAndStyleLabelResolvesToATexture() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")

        val labels = HeroAbilityLabels.signs + HeroAbilityLabels.combatStyles
        assertTrue(labels.size >= 100, "expected the full ability grid, got " + labels.size)
        for (label in labels) {
            val resref = AbilityIcons.iconResref(label)
            assertNotNull(resref, "no icon resref for ability '" + label + "'")
            assertTrue(environment.resourceFiles.containsKey(resref!! + ".dds"), "texture missing for '" + label + "': " + resref)
        }
    }

    @Test
    fun everyTalentLabelResolvesToATexture() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val library = environment.icons

        val labels = ArrayList<String>()
        val attributeNames = listOf("Strength", "Dexterity", "Endurance", "Intelligence")
        for (attribute in attributeNames) {
            for (level in 1..5) {
                labels.add(attribute + level)
                labels.add(attribute + level + " Upgrade1")
                labels.add(attribute + level + " Upgrade2")
            }
            // the third upgrade row starts at level 2
            for (level in 2..4) {
                labels.add(attribute + level + " Upgrade3")
            }
        }
        assertTrue(labels.size >= 70, "expected the full talent grid, got " + labels.size)
        library.primeTalentTalents(labels)
        val deadline = System.currentTimeMillis() + 20_000
        for (label in labels) {
            var icon = library.talentIcon(label, 18)
            while (icon == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
                icon = library.talentIcon(label, 18)
            }
            assertNotNull(icon, "no icon decoded for talent '" + label + "'")
        }
    }

    @Test
    fun everyAbilityLabelResolvesToAGameName() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val labels = HeroAbilityLabels.attributes + HeroAbilityLabels.signs + HeroAbilityLabels.combatStyles
        for (label in labels) {
            val name = AbilityNames.displayName(environment, label)
            assertTrue(!name.contains("·"), "label '$label' fell back to the synthetic name '$name'")
            assertTrue(!name.contains("_"), "name for '$label' looks like a raw key: '$name'")
        }
        // spot-check the curated table against the real TLK headings
        assertEquals("Position", AbilityNames.displayName(environment, "Strength2 Upgrade1"))
        assertEquals("Flaying", AbilityNames.displayName(environment, "Dexterity1 Upgrade1"))
        assertEquals("Cut at the Jugular I", AbilityNames.displayName(environment, "StyleSteelStrong1 Upgrade1"))
        assertEquals("Gust", AbilityNames.displayName(environment, "Aard2 Upgrade2"))
        assertEquals("Barrier II", AbilityNames.displayName(environment, "Quen2 Upgrade1"))
        assertEquals("Strong Steel (level 3)", AbilityNames.displayName(environment, "StyleSteelStrong3"))
    }

    @Test
    fun itemIconResrefFollowsTheIitChain() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val library = environment.icons

        assertEquals("iit_stlswd_001", library.itemIconResref(1, 1, "it_stlswd_001"))
        assertEquals("iit_stlswd_002", library.itemIconResref(1, 2, "it_stlswd_rrr"))
        assertEquals("iit_dice_001", library.itemIconResref(49, 1, "dice_adv_001"))
        assertEquals("iit_neckl_001", library.itemIconResref(20, 1, "it_amulet_001"))
        assertEquals("iit_quest_232", library.itemIconResref(40, 232, "it_key_004"))
        // unknown appearance numbers fall back to the template resref texture
        assertEquals("it_amulet_001", library.itemIconResref(20, 99, "it_amulet_001"))
        assertEquals(IconLibrary.PLACEHOLDER, library.itemIconResref(53, 0, "w_h_alchemy"))
    }

    @Test
    fun decodesRealIconTextures() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")

        val placeholder = DdsDecoder.decode(texture(environment, "question_mark"))
        assertEquals(32, placeholder.width)
        assertEquals(32, placeholder.height)

        val icon = DdsDecoder.decode(texture(environment, "iit_stlswd_001"))
        assertTrue(icon.width > 0 && icon.height > 0)
        assertTrue(icon.argb.any { it != 0 }, "sword icon decoded to fully transparent pixels")

        val ability = DdsDecoder.decode(texture(environment, "ui_ab_aar1"))
        assertTrue(ability.width > 0 && ability.height > 0)
        assertTrue(ability.argb.any { it != 0 }, "sign icon decoded to fully transparent pixels")
    }

    @Test
    fun iconLibraryProducesScaledIconsAsynchronously() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val library = environment.icons

        library.primeAbilities(listOf("Aard1"))
        val abilityIcon = poll(10_000) { library.abilityIcon("Aard1", 18) }
        assertNotNull(abilityIcon, "ability icon did not decode in time")
        assertEquals(18, abilityIcon!!.width)

        val swordFields = templateFields(environment, "it_stlswd_001")
        library.primeTemplates(listOf(ItemTemplate(swordFields)))
        val itemIcon = poll(10_000) { library.itemIcon(swordFields) }
        assertNotNull(itemIcon, "item icon did not decode in time")
        // swords take a 2x5-slot cell: long side 48, portrait aspect
        assertEquals(48, itemIcon!!.height)
        assertTrue(itemIcon.width < itemIcon.height, "sword icon should be portrait, was ${itemIcon.width}x${itemIcon.height}")
    }

    @Test
    fun equipmentIconPathRetainsMoreSourcePixelsThanTheInventoryThumbnail() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val library = environment.icons
        val swordFields = templateFields(environment, "it_stlswd_001")
        library.primeTemplates(listOf(ItemTemplate(swordFields)))

        val thumbnail = poll(10_000) { library.itemViewIcon(1, 1, "it_stlswd_001") }
        val equipment = poll(10_000) { library.itemViewIconFullResolution(1, 1, "it_stlswd_001") }

        assertNotNull(thumbnail)
        assertNotNull(equipment)
        assertTrue(
            maxOf(equipment!!.width, equipment.height) > maxOf(thumbnail!!.width, thumbnail.height),
            "Equipment should retain more source pixels than the ${thumbnail.width}x${thumbnail.height} inventory thumbnail"
        )
    }

    @Test
    fun everyInventoryTextureFlipsExceptTheAbilityAtlas() {
        val environment = environment() ?: return assumeTrue(false, "game install not present")
        val library = environment.icons

        for (flipped in listOf(
            "iit_stlswd_001", "iit_svswd_006", "iit_potion_004", "iit_drink_001",
            "iit_food_001", "iit_gem_001", "iit_grease_020", "iit_bomb_006", "iit_trophy_001",
            "iit_scroll_014", "it_scroll_115", "iit_ingr_026", "it_ingr_026", "iit_book_001",
            // interface art: nav medallions, container emblems, the HUD atlas
            "ui_chr_ti01", "ui_jrn_ti01", "ui_inv_ti01",
            "ui_inv_bag", "ui_inv_potion", "ui_inv_quest", "ui_hud_buttons"
        )) {
            assertTrue(library.needsVerticalFlip(flipped), flipped + " is stored bottom-up in the game archives")
        }
        assertTrue(!library.needsVerticalFlip("ui_ab_aar1"))
        assertTrue(!library.needsVerticalFlip(IconLibrary.PLACEHOLDER))
    }

    private fun <T> poll(timeoutMs: Long, probe: () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var result = probe()
        while (result == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            result = probe()
        }
        return result
    }

    private fun templateFields(environment: AppEnvironment, resref: String): DBList {
        val database = Database(environment)
        resourceStream(environment, resref + ".uti").use { input ->
            database.load(input)
        }
        val fieldList = database.getTopLevelStruct()!!.getValue() as DBList
        fieldList.setElement("TemplateResRef", DBElement(11, 0, "TemplateResRef", resref))
        return fieldList
    }

    private fun texture(environment: AppEnvironment, resref: String): ByteArray {
        return resourceStream(environment, resref + ".dds").use { it.readBytes() }
    }

    private fun resourceStream(environment: AppEnvironment, fileName: String): InputStream {
        return when (val resource = environment.resourceFiles[fileName]) {
            is File -> FileInputStream(resource)
            is KeyEntry -> resource.getInputStream()
            else -> throw IllegalStateException(fileName + " is not present in the resource scan")
        }
    }

    private fun environment(): AppEnvironment? {
        val installData = File("C:\\Games\\The Witcher Enhanced Edition\\Data")
        val mainKey = File(installData, "main.key")
        if (!mainKey.isFile) {
            return null
        }
        val stringsFile = File(installData, "dialog_3.tlk")
        val environment = AppEnvironment()
        environment.fileSeparator = "\\"
        environment.languageID = 3
        if (stringsFile.isFile) {
            environment.stringsDatabase = StringsDatabase(stringsFile)
        }
        environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))
        return environment
    }
}
