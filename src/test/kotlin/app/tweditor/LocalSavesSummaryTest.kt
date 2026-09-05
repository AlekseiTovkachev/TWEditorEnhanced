package app.tweditor

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class LocalSavesSummaryTest {
    @Test
    fun everyLocalSaveYieldsACompleteSummary() {
        val saves = SaveSeamSupport.localSaves()
        assumeTrue(saves.isNotEmpty(),
            "no local saves in '" + System.getProperty("tweditor.localSaves", ".local-saves") + "' - drop *.TheWitcherSave files there to exercise them (they are gitignored and stay local)")

        val started = System.currentTimeMillis()
        for (save in saves) {
            val summary = SaveSummaryReader.read(environment, save)

            assertTrue(summary.saveName.isNotEmpty(), save.getName() + ": save name must not be empty")
            assertTrue(summary.level >= 0, save.getName() + ": level must be readable, was " + summary.level)
            assertNotNull(summary.screenshot, save.getName() + ": screenshot entry must decode")
            assertEquals(summary.screenshot!!.width * summary.screenshot.height, summary.screenshot.argb.size,
                save.getName() + ": screenshot pixels must match the dimensions")
        }
        val elapsed = System.currentTimeMillis() - started
        println("scanned " + saves.size + " local saves in " + elapsed + "ms")
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
