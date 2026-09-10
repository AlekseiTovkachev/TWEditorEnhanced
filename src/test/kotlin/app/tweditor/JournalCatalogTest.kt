package app.tweditor

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The journal.2da catalog is tab-separated with a leading unnamed index
 * column; a parser that trims the header shifts every column by one and maps
 * nothing, which silently empties the Journal workspace's locked-entry offers.
 */
class JournalCatalogTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun catalogRowsKeepCategoryAndEntryIdAligned() {
        val file = Files.createTempFile(tempDir, "journal", ".2da").toFile()
        file.writeText(
            "2DA V2.0\r\n" +
                "\r\n" +
                "\tCategory   \tPicture        \tEntryId            \tCategoryOverride\tBigPicture\tSexPicture     \tAlchemyRecipe\r\n" +
                "0\tVisited   \t****           \tQ1012_For_sale     \t****            \t****      \t****           \t****         \r\n" +
                "1\tPlot      \t****           \tQ1014_wanted       \t****            \t****      \t****           \t****         \r\n" +
                "159\tGirlfriend\tjp_village1    \tvillage1/sex       \tcharacter       \t****      \tsp_sex_village1\t****         \r\n" +
                "166\tbestiary  \tjp_bst_kosh    \tkosh/s/1           \tboss            \t****      \t****           \t****         \r\n" +
                "633\tCharacter \t****           \tq3042_zygfryd      \tHidden          \t****      \t****           \t****         \r\n" +
                "839\tInfo      \tjp_p_temer     \ttemeria/info       \tPlace           \t1         \t****           \t****         \r\n" +
                "1030\tquebrith  \tjp_q_alchemy   \tquebrith_alchemy   \t****            \t****      \t****           \t****         \r\n"
        )
        val environment = AppEnvironment()
        environment.resourceFiles["journal.2da"] = file

        val rows = JournalCatalog.catalogEntries(environment)

        assertEquals(7, rows.size)
        assertEquals("Visited", rows[0].category)
        assertEquals("Q1012_For_sale", rows[0].entryId)
        assertEquals("bestiary", rows[3].category)
        assertEquals("kosh/s/1", rows[3].entryId)
        assertEquals("Character", rows[4].category)
        assertEquals("q3042_zygfryd", rows[4].entryId)
        assertEquals("quebrith", rows[6].category)
        assertEquals("quebrith_alchemy", rows[6].entryId)
        assertTrue(rows.none { it.category.equals("0", ignoreCase = true) }, "row indices must not be read as categories")
    }

    @Test
    fun mappedSectionsOfferTheInstalledCatalog() {
        val file = Files.createTempFile(tempDir, "journal", ".2da").toFile()
        file.writeText(
            "2DA V2.0\r\n" +
                "\r\n" +
                "\tCategory   \tPicture  \tEntryId        \tCategoryOverride\tBigPicture\tSexPicture\tAlchemyRecipe\r\n" +
                "0\tCharacter  \t****     \tzoltan/ras     \t****            \t****      \t****      \t****         \r\n" +
                "1\tPlace      \t****     \twyzim/info     \t****            \t****      \t****      \t****         \r\n" +
                "2\tbestiary   \t****     \tghoul/w/1      \t****            \t****      \t****      \t****         \r\n" +
                "3\trecipe     \t****     \tit_potion_001  \t****            \t****      \t****      \t****         \r\n" +
                "4\tvitriol    \t****     \tvitriol_001    \t****            \t****      \t****      \t****         \r\n" +
                "5\tinfo       \t****     \tflame/info     \t****            \t****      \t****      \t****         \r\n" +
                "6\ttutorial   \t****     \ttutorial35     \t****            \t****      \t****      \t****         \r\n" +
                "7\tPlot       \t****     \tq1014_wanted   \t****            \t****      \t****      \t****         \r\n"
        )
        val environment = AppEnvironment()
        environment.resourceFiles["journal.2da"] = file

        val sectioned = JournalCatalog.catalogEntries(environment)
            .groupBy { sectionFor(it.category, it.picture) }
        assertEquals(
            setOf(
                JournalSection.CHARACTERS, JournalSection.LOCATIONS, JournalSection.MONSTERS, JournalSection.FORMULA,
                JournalSection.INGREDIENTS, JournalSection.GLOSSARY, JournalSection.TUTORIALS
            ),
            sectioned.keys.filterNotNull().toSet(),
            "every editable catalog category must land in its section; quest categories stay excluded"
        )
        assertEquals(1, sectioned[JournalSection.CHARACTERS]!!.size)
        assertEquals(1, sectioned[JournalSection.MONSTERS]!!.size)
        assertEquals(1, sectioned[JournalSection.FORMULA]!!.size)
        assertEquals(1, sectioned[JournalSection.INGREDIENTS]!!.size)
        assertEquals(1, sectioned[JournalSection.GLOSSARY]!!.size)
        assertEquals(1, sectioned[JournalSection.TUTORIALS]!!.size)
    }

    @Test
    fun ingredientRowsResolveNamesThroughTheItemTemplate() {
        val catalogFile = Files.createTempFile(tempDir, "journal", ".2da").toFile()
        catalogFile.writeText(
            "2DA V2.0\r\n" +
                "\r\n" +
                "\tCategory   \tPicture        \tEntryId            \tCategoryOverride\tBigPicture\tSexPicture     \tAlchemyRecipe\r\n" +
                "0\tvitriol    \tjp_vitriol1    \tvitriol1           \t****            \t****      \t****           \t****         \r\n" +
                "1\tvitriol    \tje_ingr_166    \tvitriol2           \t****            \t****      \t****           \t****         \r\n" +
                "2\tCharacter  \t****           \tzoltan/ras         \t****            \t****      \t****           \t****         \r\n" +
                "3\tunique     \tje_ingr_018    \tunique11           \t****            \t****      \t****           \t****         \r\n" +
                "4\tunique     \tjp_unique1     \tunique1            \t****            \t****      \t****           \t****         \r\n"
        )
        val environment = AppEnvironment()
        environment.resourceFiles["journal.2da"] = catalogFile
        environment.itemTemplates.add(template(environment, "it_ingr_166", "Goose fat"))
        environment.itemTemplates.add(template(environment, "it_ingr_018", "Barghest skull"))

        val rows = JournalCatalog.catalogEntries(environment)

        assertEquals("Vitriol1", JournalCatalog.rowLabel(environment, rows[0].category, rows[0].entryId, rows[0].picture))
        assertEquals("Goose fat", JournalCatalog.rowLabel(environment, rows[1].category, rows[1].entryId, rows[1].picture))
        assertEquals("Zoltan (ras)", JournalCatalog.rowLabel(environment, rows[2].category, rows[2].entryId, rows[2].picture))
        assertEquals("Barghest skull", JournalCatalog.rowLabel(environment, rows[3].category, rows[3].entryId, rows[3].picture))
        assertEquals("Unique 1", JournalCatalog.rowLabel(environment, rows[4].category, rows[4].entryId, rows[4].picture))
    }

    @Test
    fun ingredientRowsWithoutATemplateFallBackToThePicture() {
        val catalogFile = Files.createTempFile(tempDir, "journal", ".2da").toFile()
        catalogFile.writeText(
            "2DA V2.0\r\n" +
                "\r\n" +
                "\tCategory   \tPicture        \tEntryId            \tCategoryOverride\tBigPicture\tSexPicture     \tAlchemyRecipe\r\n" +
                "0\tvitriol    \tje_ingr_051    \tvitriol4           \t****            \t****      \t****           \t****         \r\n"
        )
        val environment = AppEnvironment()
        environment.resourceFiles["journal.2da"] = catalogFile

        val rows = JournalCatalog.catalogEntries(environment)

        assertEquals("Ingredient je_ingr_051", JournalCatalog.rowLabel(environment, rows[0].category, rows[0].entryId, rows[0].picture))
    }

    @Test
    fun uniqueCatalogRowsRouteByPicture() {
        val catalogFile = Files.createTempFile(tempDir, "journal", ".2da").toFile()
        catalogFile.writeText(
            "2DA V2.0\r\n" +
                "\r\n" +
                "\tCategory   \tPicture        \tEntryId            \tCategoryOverride\tBigPicture\tSexPicture     \tAlchemyRecipe\r\n" +
                "0\tunique     \tje_ingr_002    \tunique2            \t****            \t****      \t****           \t****         \r\n" +
                "1\tunique     \tjp_unique1     \tunique1            \t****            \t****      \t****           \t****         \r\n"
        )
        val environment = AppEnvironment()
        environment.resourceFiles["journal.2da"] = catalogFile

        val rows = JournalCatalog.catalogEntries(environment)
        val sectioned = rows.groupBy { sectionFor(it.category, it.picture) }
        assertEquals(listOf("unique2"), sectioned[JournalSection.INGREDIENTS]!!.map { it.entryId })
        assertEquals(listOf("unique1"), sectioned[JournalSection.GLOSSARY]!!.map { it.entryId })
    }

    @Tag("local")
    @Test
    fun installedCatalogMapsEverySection() {
        val mainKey = File("C:\\Games\\The Witcher Enhanced Edition\\Data\\main.key")
        assumeTrue(mainKey.isFile, "game install not present")
        val environment = AppEnvironment()
        environment.fileSeparator = "\\"
        environment.languageID = 3
        environment.resourceFiles = Main.resourceFilesFrom(KeyDatabase(environment, mainKey.path))

        val rows = JournalCatalog.catalogEntries(environment)
        assertTrue(rows.size >= 700, "expected the full journal catalog, got " + rows.size)
        val byCategory = rows.groupBy { it.category }
        assertTrue((byCategory["Character"]?.size ?: 0) >= 200, "expected the character catalog, got " + byCategory["Character"])
        assertTrue((byCategory["bestiary"]?.size ?: 0) >= 100, "expected the bestiary catalog, got " + byCategory["bestiary"])
        assertTrue(
            rows.none { it.category.firstOrNull()?.isDigit() == true },
            "row indices must never be read as categories"
        )
        val mappedSections = rows.mapNotNull { sectionFor(it.category, it.picture) }.toSet()
        assertEquals(
            setOf(
                JournalSection.CHARACTERS, JournalSection.LOCATIONS, JournalSection.MONSTERS, JournalSection.FORMULA,
                JournalSection.INGREDIENTS, JournalSection.GLOSSARY, JournalSection.TUTORIALS
            ),
            mappedSections,
            "every editable catalog category must land in its section"
        )
    }

    private fun sectionFor(category: String, picture: String): JournalSection? = when (category.lowercase()) {
        "unique" -> if (picture.startsWith("je_ingr", ignoreCase = true)) JournalSection.INGREDIENTS else JournalSection.GLOSSARY
        "character" -> JournalSection.CHARACTERS
        "place" -> JournalSection.LOCATIONS
        "bestiary" -> JournalSection.MONSTERS
        "recipe", "recipe_oil", "recipe_bomb" -> JournalSection.FORMULA
        "hydragenum", "vermilion", "rebis", "quebrith", "aether", "vitriol" -> JournalSection.INGREDIENTS
        "info" -> JournalSection.GLOSSARY
        "tutorial", "alchemy" -> JournalSection.TUTORIALS
        else -> null
    }

    private fun template(environment: AppEnvironment, resref: String, name: String): ItemTemplate {
        val fields = DBList(environment, 1)
        fields.addElement(DBElement(DBElement.INT, 0, "BaseItem", 44))
        fields.addElement(DBElement(DBElement.INT, 0, "ModelPart1", 1))
        fields.addElement(DBElement(DBElement.RESOURCE, 0, "TemplateResRef", resref))
        fields.addElement(DBElement(DBElement.RESOURCE, 0, "LocalizedName", name))
        return ItemTemplate(fields)
    }
}
