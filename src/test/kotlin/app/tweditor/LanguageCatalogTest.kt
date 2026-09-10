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
        Files.write(dataDir.resolve("languages.2da"), SYNTHETIC_LANGUAGES_2DA.toByteArray(Charsets.UTF_8))

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
            """
            2DA V2.0

            \tid\tname
            0\t3\tTotallyCustomLabel
            """.trimIndent().replace("\\t", "\t").toByteArray(Charsets.UTF_8)
        )
        val languages2da = dataDir.resolve("languages.2da").toFile()

        assertEquals("English", LanguageCatalog.displayNameFor(3, languages2da))
        assertEquals("Русский", LanguageCatalog.displayNameFor(14, languages2da))
        assertEquals("Language 99", LanguageCatalog.displayNameFor(99, languages2da))
    }

    @Test
    fun the2daParserReadsTheStandardRowShape() {
        val dataDir = Files.createTempDirectory("tweditor-2da")
        val languages2da = dataDir.resolve("languages.2da").toFile()
        languages2da.writeText(SYNTHETIC_LANGUAGES_2DA, Charsets.UTF_8)

        val labels = LanguageCatalog.languages2daLabels(languages2da)
        assertEquals("FinalEnglish_Short", labels[3])
        assertEquals("Polish", labels[5])
        assertEquals("Russian", labels[14])
        assertTrue(!labels.containsKey(0) || labels[0] == "Debug")
    }

    private companion object {
        val SYNTHETIC_LANGUAGES_2DA = """
            2DA V2.0

            \tid\tname              \tcodepage\tphonemeSet\tFallBack          \tFallBackInfo\tFonts    \tCharset\tStrRef
            0\t0 \tDebug            \t1252    \tenPhonemes\t****             \t0           \t****    \t****   \t****
            3\t3 \tFinalEnglish_Short\t1252    \tenPhonemes\tFinalEnglish     \t0           \t****    \t****   \t2665
            4\t5 \tPolish           \t1250    \tenPhonemes\tDebug            \t0           \t****    \t****   \t2666
            9\t14\tRussian          \t1251    \tenPhonemes\tFinalEnglish_Short\t0           \tfonts_rus\t****   \t2671
        """.trimIndent().replace("\\t", "\t")
    }
}
