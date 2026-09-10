package app.tweditor

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class TransactionalSaveTest {
    @Test
    fun encodingFailureLeavesTheOriginalReadableAndByteIdentical(@TempDir tempDir: Path) {
        val loaded = loadEditedFixture(tempDir)
        val save = loaded.saveDatabase!!.getFile()
        val originalBytes = Files.readAllBytes(save.toPath())

        loaded.saveDatabase!!.getEntry("player.utc")!!
            .setResourceFile(tempDir.resolve("missing.utc").toFile(), 0, 1)

        assertThrows(IOException::class.java) {
            loaded.saveDatabase!!.saveTransactional()
        }

        assertTrue(loaded.session.isDataModified(), "an encoding failure must leave the draft dirty")
        assertOriginalIsUntouched(save, originalBytes)
    }

    @Test
    fun parserValidationFailureRetainsTheCompleteCandidateAndKeepsTheSessionDirty(@TempDir tempDir: Path) {
        val loaded = loadEditedFixture(tempDir)
        val originalBytes = Files.readAllBytes(loaded.saveDatabase!!.getFile().toPath())

        val failure = assertThrows(SaveWriteException::class.java) {
            loaded.saveDatabase!!.saveTransactional(
                validator = { throw IOException("injected candidate validation failure") }
            )
        }

        assertTrue(failure.candidateFile.isFile, "a complete candidate must be retained for diagnosis")
        assertTrue(loaded.session.isDataModified(), "a failed Save must leave the draft dirty")
        assertOriginalIsUntouched(loaded.saveDatabase!!.getFile(), originalBytes)
    }

    @Test
    fun parserReloadFailureRetainsTheCandidateAndLeavesTheOriginalUntouched(@TempDir tempDir: Path) {
        val loaded = loadEditedFixture(tempDir)
        val originalBytes = Files.readAllBytes(loaded.saveDatabase!!.getFile().toPath())

        val failure = assertThrows(SaveWriteException::class.java) {
            loaded.saveDatabase!!.saveTransactional(
                afterCandidateWritten = { candidate ->
                    Files.write(candidate.toPath(), byteArrayOf(0x01, 0x02, 0x03))
                }
            )
        }

        assertTrue(failure.candidateFile.isFile, "a malformed candidate must be retained for diagnosis")
        assertOriginalIsUntouched(loaded.saveDatabase!!.getFile(), originalBytes)
    }

    @Test
    fun replacementFailureRetainsTheCandidateAndLeavesTheOriginalUntouched(@TempDir tempDir: Path) {
        val loaded = loadEditedFixture(tempDir)
        val originalBytes = Files.readAllBytes(loaded.saveDatabase!!.getFile().toPath())

        val failure = assertThrows(SaveWriteException::class.java) {
            loaded.saveDatabase!!.saveTransactional(
                replacer = { _, _ -> throw IOException("injected replacement failure") }
            )
        }

        assertTrue(failure.candidateFile.isFile, "a candidate that could not be installed must be retained")
        assertOriginalIsUntouched(loaded.saveDatabase!!.getFile(), originalBytes)
    }

    @Test
    fun successfulReplacementReopensTheFinalDestination(@TempDir tempDir: Path) {
        val loaded = loadEditedFixture(tempDir)
        val save = loaded.saveDatabase!!.getFile()
        loaded.saveDatabase!!.saveTransactional()

        val reloaded = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        reloaded.load()
        assertTrue(reloaded.entries.isNotEmpty(), "the final destination must be a readable Save archive")
    }

    private fun loadEditedFixture(tempDir: Path): SaveSeamSupport.Loaded {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val loaded = SaveSeamSupport.load(SaveSeamSupport.createEnvironment(), save, tempDir)
        loaded.player!!.setInteger("Gold", 500)
        loaded.session.setDataModified(true)
        return loaded
    }

    private fun assertOriginalIsUntouched(save: File, originalBytes: ByteArray) {
        assertArrayEquals(originalBytes, Files.readAllBytes(save.toPath()), "the original Save must remain byte-identical")
        val reparsed = SaveDatabase(SaveSeamSupport.createEnvironment(), save)
        reparsed.load()
    }
}
