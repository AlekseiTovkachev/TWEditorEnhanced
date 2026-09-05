package app.tweditor

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object SavePipeline {
    /**
     * Points the session at a fresh copy of the open save: the copy keeps every
     * on-disk entry, is renamed to the new save's name (the archive paths carry
     * the save name as their first component), and becomes the save target.
     * The original file is left untouched.
     */
    fun rebindToCopy(session: GameSession, environment: AppEnvironment, target: File) {
        val saveDatabase = requireNotNull(session.saveDatabase) { "No save file is open" }
        val source = requireNotNull(saveDatabase.getFile()) { "Open save has no file" }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val newSaveDatabase = SaveDatabase(environment, target)
        newSaveDatabase.load()
        val newName = newSaveDatabase.getName()
        newSaveDatabase.setSavePrefix(newName + environment.fileSeparator)
        newSaveDatabase.repathEntries()
        val oldId = saveDatabase.getName().substring(0, 6)
        val newId = newName.substring(0, 6)
        if (oldId != newId) {
            newSaveDatabase.renameEntry("save_$oldId.smm", "save_$newId.smm")
            newSaveDatabase.renameEntry("$oldId.tga", "$newId.tga")
            session.setSmmName("save_$newId.smm")
        }
        session.saveDatabase = newSaveDatabase
    }
}
