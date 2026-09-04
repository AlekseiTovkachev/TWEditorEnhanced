package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

class SaveBackupTest {
    @Test
    fun firstSessionWriteBacksUpThePristineSaveAndPersistsTheEdit(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristine = SaveDatabase(environment, save)
        pristine.load()
        val pristineBytes = Files.readAllBytes(save.toPath())
        val before = SaveSeamSupport.entryDigests(pristine)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        assertFalse(loaded.session.hasSaveBackup(), "no backup exists before the first write")

        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)

        val backup = SaveBackup(save)
        assertEquals(save.getName() + ".bak", backup.backupFile.getName(),
            "the backup is a sibling file of the save")
        assertTrue(backup.hasBackup(), "the first session write must create the sibling backup")
        assertArrayEquals(pristineBytes, Files.readAllBytes(backup.backupFile.toPath()),
            "the backup must be a byte-identical copy of the save as it was before the first write")

        val repacked = SaveDatabase(environment, save)
        repacked.load()
        val rewritten = SaveSeamSupport.changedEntries(before, SaveSeamSupport.entryDigests(repacked))
        val allowedToChange = setOf(loaded.modName!!, "player.utc", loaded.smmName!!)
        assertTrue(allowedToChange.containsAll(rewritten),
            "entries outside the module .sav container, player.utc and the .smm file changed: " + rewritten)

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(500, reloaded.player!!.getInteger("Gold"),
            "the edit must persist through the write/reload round trip")
    }

    @Test
    fun subsequentSessionWritesDoNotOverwriteTheBackup(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristineBytes = Files.readAllBytes(save.toPath())
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)
        val backup = SaveBackup(save)
        val backupBytes = Files.readAllBytes(backup.backupFile.toPath())

        loaded.player!!.setInteger("Gold", 999)
        SaveSeamSupport.save(loaded)

        assertArrayEquals(backupBytes, Files.readAllBytes(backup.backupFile.toPath()),
            "a later write in the same session must not re-back-up")
        assertArrayEquals(pristineBytes, Files.readAllBytes(backup.backupFile.toPath()),
            "the backup keeps the pre-first-write bytes of the session")

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(999, reloaded.player!!.getInteger("Gold"))
    }

    @Test
    fun reopeningAfterCloseStartsANewBackupCycle(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val session = GameSession(tempDir.toFile())
        val loaded = SaveSeamSupport.loadInto(environment, save, tempDir, session)
        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)
        session.close()

        val reopened = SaveSeamSupport.loadInto(environment, save, tempDir, session)
        val preWriteBytes = Files.readAllBytes(save.toPath())
        reopened.player!!.setInteger("Gold", 999)
        SaveSeamSupport.save(reopened)

        val backup = SaveBackup(save)
        assertArrayEquals(preWriteBytes, Files.readAllBytes(backup.backupFile.toPath()),
            "reopening the save starts a new session whose first write replaces the previous backup")

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(999, reloaded.player!!.getInteger("Gold"))
    }

    @Test
    fun closingTheSaveClearsTheBackupState(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)
        assertTrue(loaded.session.hasSaveBackup())

        loaded.session.close()
        assertFalse(loaded.session.hasSaveBackup(), "a closed session has no save to back up")
    }

    @Test
    fun restoreFromBackupRevertsTheSaveFileToTheBackupBytes(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val pristineBytes = Files.readAllBytes(save.toPath())
        val loaded = SaveSeamSupport.load(environment, save, tempDir)
        loaded.player!!.setInteger("Gold", 500)
        SaveSeamSupport.save(loaded)

        assertTrue(loaded.session.hasSaveBackup())
        loaded.session.restoreSaveBackup()
        assertArrayEquals(pristineBytes, Files.readAllBytes(save.toPath()),
            "restoring must put the pre-first-write bytes back on disk")

        val reloaded = SaveSeamSupport.load(environment, save, tempDir)
        assertEquals(0, reloaded.player!!.getInteger("Gold"),
            "the restored save must parse back to the original facts")
    }

    @Test
    fun restoringWithoutABackupFails(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(environment, save, tempDir)

        assertFalse(loaded.session.hasSaveBackup())
        assertThrows(IOException::class.java) { loaded.session.restoreSaveBackup() }
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
