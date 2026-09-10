package app.tweditor

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LanguageSwitchSeamTest {
    private var openEnvironment: AppEnvironment? = null

    @AfterEach
    fun releaseContentTlkHandles() {
        runCatching { openEnvironment?.stringsDatabase?.close() }
        openEnvironment = null
    }
    @Test
    fun setLanguageSwapsTheContentTlkAndPersistsTheChoice(@TempDir tempDir: Path) {
        val environment = syntheticInstall(tempDir)
        val dialog3 = File(environment.installDataPath!!, "dialog_3.tlk")
        environment.stringsDatabase = StringsDatabase(dialog3)

        assertEquals("Steel sword", environment.getString(REFERENCE))
        environment.setLanguage(EditorLanguage(14, "Русский"))
        assertEquals(14, environment.languageID)
        assertEquals("Стальной меч", environment.getString(REFERENCE))
        assertEquals("14", environment.properties.getProperty("editor.language"))

        environment.setLanguage(EditorLanguage(3, "English"))
        assertEquals("Steel sword", environment.getString(REFERENCE))
    }

    @Test
    fun setLanguageRejectsUnavailableLanguagesWithoutChangingTheState(@TempDir tempDir: Path) {
        val environment = syntheticInstall(tempDir)
        environment.stringsDatabase = StringsDatabase(File(environment.installDataPath!!, "dialog_3.tlk"))
        environment.setLanguage(EditorLanguage(14, "Русский"))

        assertThrows(IOException::class.java) {
            environment.setLanguage(EditorLanguage(99, "Language 99"))
        }
        assertEquals(14, environment.languageID)
        assertEquals("Стальной меч", environment.getString(REFERENCE))
    }

    @Test
    fun aMissingSelectedLanguageSubstringFallsBackToEnglishForDisplay(@TempDir tempDir: Path) {
        val environment = syntheticInstall(tempDir)
        environment.languageID = 14

        val englishOnly = LocalizedString(-1)
        englishOnly.addSubstring(LocalizedSubstring("Steel sword", 3, 0))
        val list = DBList(environment, 1)
        list.addElement(DBElement(12, 0, "LocalizedName", englishOnly))
        assertEquals("Steel sword", list.getString("LocalizedName"))

        val russian = LocalizedString(-1)
        russian.addSubstring(LocalizedSubstring("Стальной меч", 14, 0))
        val russianList = DBList(environment, 1)
        russianList.addElement(DBElement(12, 0, "LocalizedName", russian))
        assertEquals("Стальной меч", russianList.getString("LocalizedName"))
    }

    @Test
    fun aRenameUnderRussianLandsInSlot28AndLeavesOtherSlotsAndEntriesUntouched(@TempDir tempDir: Path) {
        val environment = syntheticInstall(tempDir)
        val saveFile = SaveSeamSupport.copyFixtureTo(tempDir, SaveSeamSupport.Fixture.EQUIPMENT)
        val loaded = SaveSeamSupport.load(environment, saveFile, tempDir)

        val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
        val view = InventoryViewState.from(loaded.session)
        val item = view.storage.firstOrNull() ?: view.equipment.flatMap { it.items }.first()
        val record = EquipmentAccess.record(loaded.session, item.id)!!
        val templateResRef = record.fields.getString("TemplateResRef")
        val englishBefore = (record.fields.getElement("LocalizedName")!!.getValue() as LocalizedString)
            .getSubstring(LanguageCatalog.ENGLISH_LANGUAGE_ID, 0)?.string

        environment.setLanguage(EditorLanguage(14, "Русский"))
        val renamed = "Меч испытателя"
        record.fields.setString("LocalizedName", renamed)

        SaveSeamSupport.save(loaded)
        val reloaded = SaveSeamSupport.load(environment, saveFile, tempDir)
        val reloadedView = InventoryViewState.from(reloaded.session)
        val reloadedItem = (reloadedView.storage + reloadedView.equipment.flatMap { dest -> dest.items })
            .first { it.templateResRef == templateResRef }
        val reloadedFields = EquipmentAccess.record(reloaded.session, reloadedItem.id)!!.fields
        val localized = reloadedFields.getElement("LocalizedName")!!.getValue() as LocalizedString
        assertEquals(renamed, localized.getSubstring(14, 0)!!.string)
        assertEquals(englishBefore, localized.getSubstring(3, 0)?.string)

        val after = SaveSeamSupport.entryDigests(reloaded.saveDatabase!!)
        SaveSeamSupport.assertUntouchedEntries(
            before,
            after,
            setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        )
    }

    private fun syntheticInstall(tempDir: Path): AppEnvironment {
        val dataDir = Files.createDirectory(tempDir.resolve("Data"))
        Files.write(
            dataDir.resolve("dialog_3.tlk"),
            SyntheticTlk.build(3, mapOf(REFERENCE to "Steel sword"))
        )
        Files.write(
            dataDir.resolve("dialog_14.tlk"),
            SyntheticTlk.build(14, mapOf(REFERENCE to "Стальной меч"))
        )
        val environment = SaveSeamSupport.createEnvironment()
        environment.installDataPath = dataDir.toString()
        openEnvironment = environment
        return environment
    }

    companion object {
        const val REFERENCE = 1000
    }
}
