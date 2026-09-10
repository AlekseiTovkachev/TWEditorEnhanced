package app.tweditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LanguageCatalogTest {
    @Test
    fun discoveryFindsLooseTlksAndNamesThemFromLanguages2da(@TempDir tempDir: Path) {
        val dataDir = Files.createDirectory(tempDir.resolve("Data"))
        Files.write(dataDir.resolve("dialog_3.tlk"), SyntheticTlk.build(3, mapOf(0 to "English base")))
        Files.write(dataDir.resolve("dialog_5.tlk"), SyntheticTlk.build(5, mapOf(0 to "Polska")))
        Files.write(dataDir.resolve("dialog_14.tlk"), SyntheticTlk.build(14, mapOf(0 to "Русская база")))
        Files.write(
            dataDir.resolve("languages.2da"),
            javaClass.getResource("/languages/synthetic-languages.2da")!!.readBytes()
        )

        val environment = SaveSeamSupport.createEnvironment()
        environment.installDataPath = dataDir.toString()

        assertEquals(
            listOf(
                EditorLanguage(3, "English"),
                EditorLanguage(5, "Polski"),
                EditorLanguage(14, "Русский")
            ),
            LanguageCatalog.discover(environment)
        )
    }

    @Test
    fun discoveryWithoutLanguages2daFallsBackToTheStandardIdTable(@TempDir tempDir: Path) {
        val dataDir = Files.createDirectory(tempDir.resolve("Data"))
        Files.write(dataDir.resolve("dialog_3.tlk"), SyntheticTlk.build(3, mapOf(0 to "English base")))
        Files.write(dataDir.resolve("dialog_14.tlk"), SyntheticTlk.build(14, mapOf(0 to "Русская база")))
        Files.write(dataDir.resolve("dialog_99.tlk"), SyntheticTlk.build(99, mapOf(0 to "mystery")))

        val environment = SaveSeamSupport.createEnvironment()
        environment.installDataPath = dataDir.toString()

        assertEquals(
            listOf(
                EditorLanguage(3, "English"),
                EditorLanguage(14, "Русский"),
                EditorLanguage(99, "Language 99")
            ),
            LanguageCatalog.discover(environment)
        )
    }

    @Test
    fun unknownLabelsFallBackToTheStandardIdTable(@TempDir tempDir: Path) {
        val dataDir = Files.createDirectory(tempDir.resolve("Data"))
        Files.write(
            dataDir.resolve("languages.2da"),
            javaClass.getResource("/languages/synthetic-languages.2da")!!.readBytes()
        )
        val languages2da = dataDir.resolve("languages.2da").toFile()

        assertEquals("FinalEnglish_Short", LanguageCatalog.languages2daLabels(languages2da)[3])
        assertEquals("English", LanguageCatalog.displayNameFor(3, languages2da))
        assertEquals("Русский", LanguageCatalog.displayNameFor(14, languages2da))
        assertEquals("Language 99", LanguageCatalog.displayNameFor(99, languages2da))
    }

    @Test
    fun the2daParserReadsTheStandardRowShape() {
        val languages2da = javaClass.getResource("/languages/synthetic-languages.2da")!!.readBytes()

        val labels = LanguageCatalog.languages2daLabels(
            Files.write(
                Files.createTempDirectory("tweditor-2da").resolve("languages.2da"),
                languages2da
            ).toFile()
        )
        assertEquals("FinalEnglish_Short", labels[3])
        assertEquals("Polish", labels[5])
        assertEquals("Russian", labels[14])
        assertTrue(!labels.containsKey(0) || labels[0] == "Debug")
    }
}
