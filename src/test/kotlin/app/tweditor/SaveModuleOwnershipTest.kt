package app.tweditor

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(30)
class SaveModuleOwnershipTest {
    @Test
    fun workingCopySaveExposesStartingModuleAndRecordedModuleSet(@TempDir tempDir: Path) {
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(
            environment,
            SaveSeamSupport.copyFixtureTo(tempDir),
            tempDir
        )

        val ownership = loaded.session.getModuleOwnership()
        assertEquals("kaer_morhen", ownership.startingModule)
        assertTrue(ownership.modules.contains("kaer_morhen"))
        assertTrue(ownership.modules.size > 1, "Meta_Mod_list should contribute module ownership entries")
        assertTrue(loaded.smmFile?.isFile == true)
    }
}
