package app.tweditor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SaveFile private constructor(
    private val session: GameSession,
    private val environment: AppEnvironment,
    private val targetFile: File?,
    private val reportProgress: (Int) -> Unit,
    private val complete: (Boolean) -> Unit,
    private val reportError: (String, Throwable) -> Unit,
    @Suppress("UNUSED_PARAMETER") private val callbackMode: Boolean
) : Thread() {
    /** Headless/asynchronous seam used by the Compose shell and file-workflow tests. */
    constructor(
        session: GameSession,
        environment: AppEnvironment,
        targetFile: File? = null,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit,
        onError: (String, Throwable) -> Unit = { text, exc -> Main.logException(text, exc) }
    ) : this(session, environment, targetFile, onProgress, onComplete, onError, true)

    private var saveSuccessful = false

    override fun run() {
        try {
            if (targetFile != null) {
                SavePipeline.rebindToCopy(session, environment, targetFile)
            }

            session.database!!.save()
            reportProgress(15)

            val resourceEntry = ResourceEntry("module.ifo", session.databaseFile)
            session.modDatabase!!.addEntry(resourceEntry)
            session.modDatabase!!.save()
            reportProgress(30)

            val modDatabase = ResourceDatabase(session.modDatabase!!.getPath())
            modDatabase.load()
            session.modDatabase = modDatabase
            reportProgress(45)

            session.saveDatabase!!.addEntry(session.getModName()!!, session.modFile)
            reportProgress(60)

            session.playerDatabase!!.save()
            session.saveDatabase!!.addEntry(session.getPlayerName()!!, session.playerFile)
            reportProgress(70)

            session.smmDatabase!!.save()
            session.saveDatabase!!.addEntry(session.getSmmName()!!, session.smmFile)
            reportProgress(80)

            if (session.isJournalDirty()) {
                FileOutputStream(session.questDatabaseFile).use { out ->
                    session.getQuestDatabase()!!.save(out)
                }
                session.saveDatabase!!.addEntry(session.getQuestDBName()!! + ".qdb", session.questDatabaseFile)
            }
            reportProgress(85)

            session.writeSave()
            reportProgress(90)

            val saveDatabase = SaveDatabase(environment, session.saveDatabase!!.getPath())
            saveDatabase.load()
            session.saveDatabase = saveDatabase

            reportProgress(100)

            this.saveSuccessful = true
        } catch (exc: DBException) {
            reportError("Unable to update save database", exc)
        } catch (exc: IOException) {
            reportError("Unable to save file", exc)
        } catch (exc: Throwable) {
            reportError("Exception while saving file", exc)
        }

        complete(saveSuccessful)
    }
}
