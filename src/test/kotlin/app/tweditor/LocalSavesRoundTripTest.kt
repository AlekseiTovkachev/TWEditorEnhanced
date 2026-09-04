package app.tweditor

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LocalSavesRoundTripTest {
    @Test
    fun localSavesRoundTripWithIdenticalFacts(@TempDir tempDir: Path) {
        val savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile()
        val saves = savesDir.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
        assumeTrue(saves != null && saves.isNotEmpty(),
            "no local saves in '" + savesDir + "' - drop *.TheWitcherSave files there to exercise them (they are gitignored and stay local)")

        for (i in saves.indices) {
            val workDir = Files.createDirectory(tempDir.resolve("save-" + i))
            val save = Files.copy(saves[i].toPath(), workDir.resolve(saves[i].getName())).toFile()
            val loaded = SaveSeamSupport.load(environment, save, workDir)
            val questCount = loaded.questCount
            val experience = loaded.player!!.getInteger("Experience")
            val hitPoints = loaded.player!!.getInteger("CurrentHitPoints")

            val before = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)
            SaveSeamSupport.save(loaded)
            val after = SaveSeamSupport.entryDigests(loaded.saveDatabase!!)

            assertEquals(before.keys, after.keys, save.getName())
            val rewritten = SaveSeamSupport.changedEntries(before, after)
            val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
            assertTrue(allowedToChange.containsAll(rewritten),
                save.getName() + ": entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

            val reloaded = SaveSeamSupport.load(environment, save, workDir)
            assertEquals(questCount, reloaded.questCount, save.getName())
            assertEquals(experience, reloaded.player!!.getInteger("Experience"), save.getName())
            assertEquals(hitPoints, reloaded.player!!.getInteger("CurrentHitPoints"), save.getName())
            assertTrue(reloaded.player!!.getInteger("Gold") >= 0, save.getName())
        }
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
