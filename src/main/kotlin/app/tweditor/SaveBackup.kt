package app.tweditor

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SaveBackup(private val saveFile: File) {
    val backupFile: File = File(saveFile.getParentFile(), saveFile.getName() + BACKUP_SUFFIX)

    fun hasBackup(): Boolean = backupFile.isFile

    @Throws(IOException::class)
    fun createBackup() {
        if (!saveFile.isFile) {
            throw IOException("Save file '" + saveFile.getPath() + "' does not exist")
        }
        Files.copy(saveFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    fun restoreBackup() {
        if (!backupFile.isFile) {
            throw IOException("Backup file '" + backupFile.getPath() + "' does not exist")
        }
        Files.copy(backupFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val BACKUP_SUFFIX = ".bak"
    }
}
