package app.tweditor

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Applies the Russian descriptions from [ModRussianLocalizationData] to the
 * owner's installed Sword Stats Rebalance templates: backs up originals into
 * the game's Mod Conflict Backups area, edits a working copy, proves the edit
 * through a parse-back of the written file, then installs and re-verifies.
 */
@Tag("local")
@Timeout(300)
class ModRussianLocalizationTest {

    private val dataDir = Path.of("C:\\Games\\The Witcher Enhanced Edition\\Data")
    private val modRoot = dataDir.resolve("Override").resolve("Swords Stats Rebalance")
    private val backupRoot = Path.of(
        "C:\\Games\\The Witcher Enhanced Edition\\Mod Conflict Backups\\Swords Stats Rebalance"
    )

    @Test
    fun applyRussianDescriptionsToSwordRebalanceTemplates() {
        assumeTrue(Files.isDirectory(modRoot), "owner's Sword Stats Rebalance install not present")

        val backupDir = prepareBackupDir()
        val environment = SaveSeamSupport.createEnvironment()
        val report = ArrayList<String>()
        val workDir = Files.createTempDirectory("tweditor-modloc")
        try {
            assertSingleLooseWinner()
            for (template in ModRussianLocalizationData.templates) {
                val target = target(template)
                assumeTrue(Files.isRegularFile(target), "missing mod template: $target")
                val expected = requireNotNull(ModRussianLocalizationData.russianDescriptions[template.resref])
                val originalBytes = Files.readAllBytes(target)
                val originalDatabase = loadDatabase(environment, originalBytes, template.resref)
                val originalRu = russianDescription(topLevelFields(originalDatabase))
                if (originalRu == expected) {
                    report.add("${template.resref}: already localized (verified only)")
                    continue
                }
                backupOriginal(backupDir, template, originalBytes)

                editRussianDescription(originalDatabase, expected)
                val working = workDir.resolve(template.resref + ".uti")
                FileOutputStream(working.toFile()).use { originalDatabase.save(it) }

                val reloaded = loadDatabase(environment, Files.readAllBytes(working), template.resref)
                assertEquals(
                    expected,
                    russianDescription(topLevelFields(reloaded)),
                    "Russian description missing after write/reload: ${template.resref}"
                )
                assertOnlyRussianDescriptionChanged(
                    template.resref,
                    originalBytes,
                    originalRu,
                    reloaded
                )

                Files.copy(working, target, StandardCopyOption.REPLACE_EXISTING)

                val installed = loadDatabase(environment, Files.readAllBytes(target), template.resref)
                assertEquals(
                    expected,
                    russianDescription(topLevelFields(installed)),
                    "installed Russian description mismatch: ${template.resref}"
                )
                report.add(
                    "${template.resref}: localized (${originalRu.length} -> ${expected.length} chars," +
                        " previous garbage=${originalRu.contains("????")})"
                )
            }
        } finally {
            workDir.toFile().deleteRecursively()
        }

        assertSingleLooseWinner()
        report.forEach { println("RU-LOCALIZATION: $it") }
        println("RU-LOCALIZATION: backup at $backupDir")
    }

    /** Read-only pass over the installed templates: Russian text present, no '?' placeholders. */
    @Test
    fun verifyInstalledRussianDescriptions() {
        assumeTrue(Files.isDirectory(modRoot), "owner's Sword Stats Rebalance install not present")

        val environment = SaveSeamSupport.createEnvironment()
        assertSingleLooseWinner()
        for (template in ModRussianLocalizationData.templates) {
            val target = target(template)
            assertTrue(Files.isRegularFile(target), "missing mod template: $target")
            val expected = requireNotNull(ModRussianLocalizationData.russianDescriptions[template.resref])
            val database = loadDatabase(environment, Files.readAllBytes(target), template.resref)
            assertEquals(
                expected,
                russianDescription(topLevelFields(database)),
                "Russian description mismatch: ${template.resref}"
            )
        }
    }

    private fun target(template: ModRussianLocalizationData.ModTemplate): Path =
        modRoot.resolve(template.directory).resolve(template.resref + ".uti")

    private fun loadDatabase(environment: AppEnvironment, bytes: ByteArray, resref: String): Database {
        val database = Database(environment)
        database.setName(resref + ".uti")
        database.load(ByteArrayInputStream(bytes))
        assertEquals("UTI ", database.getType(), "unexpected file type for $resref")
        assertEquals("V3.3", database.getVersion(), "unexpected GFF version for $resref")
        return database
    }

    private fun topLevelFields(database: Database): DBList =
        database.getTopLevelStruct()!!.getValue() as DBList

    private fun editRussianDescription(database: Database, text: String) {
        val string = topLevelFields(database).getElement("Description")!!.getValue() as LocalizedString
        string.addSubstring(LocalizedSubstring(text, 14, 0))
    }

    private fun russianDescription(fields: DBList): String {
        val element = fields.getElement("Description")
        assertNotNull(element, "template has no Description field")
        val string = element!!.getValue() as LocalizedString
        val substring = string.getSubstring(14, 0)
        return substring?.string ?: ""
    }

    private fun prepareBackupDir(): Path {
        Files.createDirectories(backupRoot)
        Files.list(backupRoot).use { stream ->
            val existing = stream
                .filter { it.fileName.toString().endsWith("russian-localization") }
                .findFirst()
            if (existing.isPresent) {
                return existing.get()
            }
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val dir = backupRoot.resolve("$stamp-russian-localization")
        Files.createDirectories(dir)
        return dir
    }

    private fun backupOriginal(backupDir: Path, template: ModRussianLocalizationData.ModTemplate, bytes: ByteArray) {
        val dir = backupDir.resolve(template.directory)
        Files.createDirectories(dir)
        val target = dir.resolve(template.resref + ".uti")
        if (!Files.exists(target)) {
            Files.write(target, bytes)
        }
    }

    private fun assertOnlyRussianDescriptionChanged(
        resref: String,
        originalBytes: ByteArray,
        originalRu: String,
        after: Database
    ) {
        val before = loadDatabase(SaveSeamSupport.createEnvironment(), originalBytes, resref)
        val beforeFields = topLevelFields(before)
        val afterFields = topLevelFields(after)
        val beforeTree = canonicalTree(beforeFields, true)
        val afterTree = canonicalTree(afterFields, true)
        assertEquals(beforeTree, afterTree, "fields outside the Russian description changed: $resref")

        val beforeRu = russianSubstringRaw(beforeFields)
        val afterRu = russianSubstringRaw(afterFields)
        assertTrue(
            beforeRu.second.string != afterRu.second.string,
            "Russian description did not change: $resref"
        )
        assertEquals(
            originalRu,
            beforeRu.second.string,
            "baseline Russian text is not the pre-edit text: $resref"
        )
        assertEquals(
            beforeRu.first,
            afterRu.first,
            "Russian substring slot position changed: $resref"
        )
    }

    private fun canonicalTree(list: DBList, ignoreRussianDescription: Boolean): String =
        list.elementList.joinToString(";") { canonicalTree(it, ignoreRussianDescription) }

    private fun russianSubstringRaw(fields: DBList): Pair<Int, LocalizedSubstring> {
        val string = fields.getElement("Description")!!.getValue() as LocalizedString
        var position = -1
        var substring: LocalizedSubstring? = null
        for (i in 0 until string.getSubstringCount()) {
            val candidate = string.getSubstring(i)
            if (candidate.language == 14 && candidate.gender == 0) {
                position = i
                substring = candidate
                break
            }
        }
        assertNotNull(substring, "no Russian substring present: $fields")
        return Pair(position, substring!!)
    }

    private fun canonicalTree(element: DBElement, ignoreRussianDescription: Boolean): String {
        val value = element.getValue()
        val valueText = when (element.getType()) {
            14, 15 -> {
                val list = value as DBList
                list.elementList.joinToString(";") { canonicalTree(it, ignoreRussianDescription) }
            }
            12 -> {
                val string = value as LocalizedString
                val skip = ignoreRussianDescription && element.getLabel() == "Description"
                (0 until string.getSubstringCount()).joinToString(",") { i ->
                    val substring = string.getSubstring(i)
                    if (skip && substring.language == 14 && substring.gender == 0) {
                        "[14/0=<ignored>]"
                    } else {
                        "[${substring.language}/${substring.gender}=${substring.string}]"
                    }
                } + "|ref=" + string.stringReference
            }
            13 -> "void[" + Base64.getEncoder().encodeToString(value as ByteArray) + "]"
            else -> value.toString()
        }
        return element.getType().toString() + "/" + element.getID() + "/" + element.getLabel() + "=" + valueText
    }

    private fun assertSingleLooseWinner() {
        val counts = HashMap<String, Int>()
        Files.walk(dataDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                counts.merge(path.fileName.toString().lowercase(), 1, Int::plus)
            }
        }
        for (template in ModRussianLocalizationData.templates) {
            val count = counts[template.resref + ".uti"] ?: 0
            assertEquals(1, count, "expected exactly one loose winner for ${template.resref}.uti under Data")
        }
        assertEquals(1, counts["weapon_abl.lua"] ?: 0, "expected exactly one weapon_abl.lua under Data")
    }
}
