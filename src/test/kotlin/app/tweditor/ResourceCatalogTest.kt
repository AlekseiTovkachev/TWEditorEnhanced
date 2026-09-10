package app.tweditor

import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(30)
class ResourceCatalogTest {

    @Test
    fun packedResourcesExposeOriginOpenThroughTheCommonResourceSeamAndLayerModuleStrings(@TempDir tempDir: Path) {
        val modules = Files.createDirectories(tempDir.resolve("modules"))
        createModule(
            modules.resolve("01-base.mod"),
            mapOf(
                "catalog.2da" to "2DA V2.0\n\nName\n0 \"from base module\"\n".toByteArray(),
                "module.tlk" to tlk(mapOf(3 to "base module label"), 4),
                "module.ifo" to byteArrayOf(9, 8, 7),
                "unknown.bin" to byteArrayOf(1, 2, 3, 4)
            )
        )
        createModule(
            modules.resolve("02-override.mod"),
            mapOf(
                "catalog.2da" to "2DA V2.0\n\nName\n0 \"from later module\"\n".toByteArray(),
                "module.tlk" to tlk(mapOf(3 to "later module label"), 4)
            )
        )
        val moduleBytesBefore = Files.readAllBytes(modules.resolve("01-base.mod"))

        val environment = AppEnvironment()
        environment.stringsDatabase = StringsDatabase(ByteArrayInputStream(tlk(emptyMap(), 1)), "dialog_3.tlk")
        val modulesFound = Main.discoverPackedModules(environment, modules.toFile())
        assertArrayEquals(moduleBytesBefore, Files.readAllBytes(modules.resolve("01-base.mod")))

        assertEquals(2, modulesFound.size)
        val catalog = environment.resourceFiles["catalog.2da"]
        assertTrue(catalog is ResourceEntry)
        assertEquals("02-override.mod", environment.owningModule("catalog.2da"))
        assertEquals(ResourceOriginKind.PACKED_MODULE, environment.resourceOrigin("catalog.2da")?.kind)
        assertTrue(environment.resourceFiles["module.ifo"] is ResourceEntry)
        assertEquals("01-base.mod", environment.owningModule("module.ifo"))
        assertEquals("from later module", requireNotNull(ResourceAccess.open(catalog!!)).use { TextDatabase(it).getString(0, "Name") })
        assertEquals("base module label", environment.getString(3))
        assertEquals("base module label", environment.getString(3, "01-base.mod"))
        assertEquals("later module label", environment.getString(3, "02-override.mod"))

        val unknown = modulesFound.first().database.getEntry("unknown.bin")
        assertNotNull(unknown)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), requireNotNull(ResourceAccess.open(unknown!!)).use { it.readBytes() })
    }

    @Test
    fun looseResourcesOverridePackedEntriesAndRetainLooseOrigin(@TempDir tempDir: Path) {
        val modules = Files.createDirectories(tempDir.resolve("modules"))
        createModule(
            modules.resolve("01-base.mod"),
            mapOf("catalog.2da" to "2DA V2.0\n\nName\n0 packed\n".toByteArray())
        )
        val environment = AppEnvironment()
        Main.discoverPackedModules(environment, modules.toFile())

        val overrides = Files.createDirectories(tempDir.resolve("Overrides").resolve("nested"))
        val override = write(overrides.resolve("catalog.2da"), "2DA V2.0\n\nName\n0 loose\n".toByteArray())
        Main.processOverrides(environment, tempDir.resolve("Overrides").toFile())

        assertEquals(override.toFile(), environment.resourceFiles["catalog.2da"])
        assertEquals(ResourceOriginKind.LOOSE_OVERRIDE, environment.resourceOrigin("catalog.2da")?.kind)
        assertEquals("loose", requireNotNull(ResourceAccess.open(environment.resourceFiles["catalog.2da"]!!)).use {
            TextDatabase(it).getString(0, "Name")
        })
    }

    @Test
    @Tag("local")
    fun ownerInstallPackedModulesAreDiscoverableWithoutCopyingTheirAssets() {
        val modules = Path.of("C:\\Games\\The Witcher Enhanced Edition\\Data\\modules")
        assumeTrue(Files.isDirectory(modules), "owner install modules directory not present")

        val environment = AppEnvironment()
        val found = Main.discoverPackedModules(environment, modules.toFile())

        assertTrue(found.isNotEmpty(), "expected at least one owner-installed packed module")
        assertTrue(found.any { it.database.getEntryCount() > 0 })
        assertTrue(environment.resourceOrigins.values.any { it.kind == ResourceOriginKind.PACKED_MODULE })
    }

    private fun createModule(module: Path, resources: Map<String, ByteArray>) {
        val payload = Files.createDirectories(module.parent.resolve(module.fileName.toString() + ".payload"))
        val database = ResourceDatabase(module.toFile())
        for ((name, bytes) in resources) {
            val file = write(payload.resolve(name), bytes)
            database.addEntry(ResourceEntry(file.toFile()))
        }
        database.save()
    }

    private fun write(path: Path, bytes: ByteArray): Path {
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return path
    }

    private fun tlk(strings: Map<Int, String>, count: Int): ByteArray {
        val stringOffset = 20 + count * 40
        val entries = ByteArrayOutputStream(count * 40)
        val data = ByteArrayOutputStream()
        for (index in 0 until count) {
            val value = strings[index]?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val entry = ByteArray(40)
            if (value.isNotEmpty()) {
                entry[0] = 1
                setInt(entry, 28, data.size())
                setInt(entry, 32, value.size)
                data.write(value)
            }
            entries.write(entry)
        }

        val header = ByteArray(20)
        "TLK V3.0".toByteArray(Charsets.US_ASCII).copyInto(header)
        setInt(header, 8, 3)
        setInt(header, 12, count)
        setInt(header, 16, stringOffset)
        return header + entries.toByteArray() + data.toByteArray()
    }

    private fun setInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
